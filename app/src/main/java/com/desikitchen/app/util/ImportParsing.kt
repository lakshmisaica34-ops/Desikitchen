package com.desikitchen.app.util

/** Shared "text → recipe" step used by Paste, Scan (OCR) and file import. */
object ImportParsing {
    /** Picks whichever of the plain-text and caption parsers found more; null when neither found ingredients or steps. */
    fun parse(text: String): RecipeTextParser.ParsedRecipe? {
        val plain = RecipeTextParser.parse(text)
        // 1.1.5: the caption parser is a bonus. If it ever fails (e.g. a regex the phone's ICU engine rejects, which
        // surfaces as ExceptionInInitializerError / NoClassDefFoundError), Paste and Scan still work via the plain parser.
        val caption = try {
            CaptionRecipeParser.parse(text).parsed
        } catch (e: Throwable) {
            if (e is OutOfMemoryError || e is InterruptedException) throw e
            ImportErrors.log("caption parser failed", e)
            RecipeTextParser.ParsedRecipe(plain.title, null, emptyList(), emptyList())
        }
        // 2026-10-29: both results are already sanitized (no nutrition / instructions in ingredients). Prefer the one that
        // has BOTH ingredients and steps; only then the bigger one. A pure size race let an "ingredients" list stuffed
        // with method lines beat a parse that had the steps.
        fun score(p: RecipeTextParser.ParsedRecipe) = (if (p.ingredients.isNotEmpty() && p.steps.isNotEmpty()) 1_000 else 0) + p.ingredients.size + p.steps.size
        val parsed = RecipeSanitizer.clean(if (score(caption) > score(plain)) caption else plain)
        return parsed.takeIf { it.ingredients.isNotEmpty() || it.steps.isNotEmpty() }
    }

    const val NOT_FOUND = "Couldn't find ingredients or steps. Try adding \"Ingredients\" and \"Method\" headings."
}
