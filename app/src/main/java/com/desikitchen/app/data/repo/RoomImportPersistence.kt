package com.desikitchen.app.data.repo

import com.desikitchen.app.data.db.PendingImportDao
import com.desikitchen.app.data.model.PendingImport
import com.desikitchen.app.util.ImportDraftCodec
import com.desikitchen.app.util.ImportPersistence

/**
 * Room-backed [ImportPersistence]: one row per queued link / parked draft / notice in `pending_imports`, plus the
 * Review editor session ([PendingImport.EDITOR] = draft, [PendingImport.EDITOR_EDITS] = in-progress form JSON).
 */
class RoomImportPersistence(private val dao: PendingImportDao) : ImportPersistence {
    override suspend fun load(): ImportPersistence.Snapshot {
        val rows = dao.getAll()
        return ImportPersistence.Snapshot(
            queue = rows.filter { it.kind == PendingImport.QUEUE && it.url.isNotBlank() }.sortedBy { it.position }.map { it.url },
            // An unreadable draft row is dropped, never rebuilt from guesses.
            parked = rows.filter { it.kind == PendingImport.PARKED }.sortedBy { it.position }.mapNotNull { r ->
                ImportDraftCodec.decode(r.draftJson)?.let { ImportPersistence.ParkedDraft(it, r.failMessage) }
            },
            notice = rows.firstOrNull { it.kind == PendingImport.NOTICE }?.failMessage?.takeIf { it.isNotBlank() },
            editor = rows.firstOrNull { it.kind == PendingImport.EDITOR }?.let { r ->
                ImportDraftCodec.decode(r.draftJson)?.let { d ->
                    ImportPersistence.EditorDraft(d, r.failMessage, edits = rows.firstOrNull { it.kind == PendingImport.EDITOR_EDITS }?.draftJson)
                }
            },
        )
    }

    override suspend fun save(snapshot: ImportPersistence.Snapshot) {
        val now = System.currentTimeMillis()
        val rows = snapshot.queue.mapIndexed { i, url -> PendingImport(kind = PendingImport.QUEUE, position = i, url = url, createdAt = now) } +
            snapshot.parked.mapIndexed { i, p ->
                PendingImport(
                    kind = PendingImport.PARKED, position = i, url = p.draft.sourceUrl.orEmpty(),
                    draftJson = ImportDraftCodec.encode(p.draft), failMessage = p.failMessage, createdAt = now,
                )
            } +
            listOfNotNull(snapshot.notice?.let { PendingImport(kind = PendingImport.NOTICE, position = 0, failMessage = it, createdAt = now) }) +
            snapshot.editor?.let { e ->
                val url = e.draft.sourceUrl.orEmpty()
                listOfNotNull(
                    PendingImport(kind = PendingImport.EDITOR, position = 0, url = url, draftJson = ImportDraftCodec.encode(e.draft), failMessage = e.failMessage, createdAt = now),
                    e.edits?.let { PendingImport(kind = PendingImport.EDITOR_EDITS, position = 0, url = url, draftJson = it, createdAt = now) },
                )
            }.orEmpty()
        dao.replaceAll(rows)
    }
}
