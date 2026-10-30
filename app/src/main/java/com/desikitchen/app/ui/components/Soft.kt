package com.desikitchen.app.ui.components

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.desikitchen.app.ui.theme.Ink
import com.desikitchen.app.ui.theme.Muted
import com.desikitchen.app.ui.theme.Canvas
import com.desikitchen.app.ui.theme.RetroGreen
import com.desikitchen.app.ui.theme.RetroGreenDeep
import com.desikitchen.app.ui.theme.SoftPistachioPale
import com.desikitchen.app.ui.theme.Tangerine
import com.desikitchen.app.ui.theme.SoftPistachio as ThemePistachio
import com.desikitchen.app.ui.theme.Chip as ChipColor

// ---- Soft shadow tokens (Design Lab soft-depth pass). Use these; don't hardcode shadow colours per call. ----
/** List-depth shadow (repeated cards/rows/tiles) @ [SoftElevList]. */
val SoftAmbientList = Color(0x1A000000)
val SoftSpotList = Color(0x14000000)
val SoftElevList = 4.dp
/** Hero-depth shadow (the one loud card) @ [SoftElevHero]. Also usable at List elev for a photo tile that reads flat. */
val SoftAmbientHero = Color(0x22000000)
val SoftSpotHero = Color(0x1A000000)
val SoftElevHero = 8.dp
/** Floating / sticky CTA bar shadow @ [SoftElevFloat]. The lift lives on the bar, not on the PillButton fill. */
val SoftAmbientFloat = Color(0x28000000)
val SoftSpotFloat = Color(0x1F000000)
val SoftElevFloat = 12.dp
/** SoftSheet (white sheet over a hero photo) @ [SoftElevSheet]. */
val SoftAmbientSheet = Color(0x1F000000)
val SoftSpotSheet = Color(0x14000000)
val SoftElevSheet = 10.dp

/** Quiet page ground under white cards (Theme SoftPistachioPale #F6F6EA). Cards/sheets/bars stay Canvas. */
val SoftPageGround: Color = SoftPistachioPale

// ---- Sai "Tangerine" palette bridge (Sai OK 2026-10-30). These used to be Soft-local literals while Theme.kt still held
// SoftSage; they now point at the Theme tokens (single source of truth) so Soft screens keep compiling unchanged. ----
/** Tangerine #FE7A25: top wash + clipboard accent. = Theme [Tangerine]. */
internal val SoftTangerine: Color = Tangerine
/** Soft Pistachio #E9E8CA: soft fills (Plus badge, selected-tab wash, secondary pills). = Theme SoftPistachio. */
internal val SoftPistachio: Color = ThemePistachio
/** Retro Green #006732: primary CTA, selected chips, See all. White text on it (7.0:1). = Theme [RetroGreen]. */
internal val SoftRetroGreen: Color = RetroGreen
/** Canvas #FFFFFF / Ink #17171A (= Theme). */
internal val SoftCanvas: Color = Canvas
internal val SoftInk: Color = Ink
/** Light neutral paste-bar border (mock: neutral hairline, not sage). */
internal val SoftFieldBorder = Color(0xFFE4E4E7)
/** Barely-there tangerine top wash: Tangerine 12% at the top fading to transparent (sits on the Canvas page). */
internal val SoftTangerineWash: Brush = Brush.verticalGradient(
    0f to SoftTangerine.copy(alpha = 0.12f),
    0.6f to SoftTangerine.copy(alpha = 0.04f),
    1f to Color.Transparent,
)

/** Soft blurry lift with token colours; pair with the same [shape] used for the background. */
fun Modifier.softShadow(elevation: Dp, shape: Shape, ambient: Color = SoftAmbientList, spot: Color = SoftSpotList): Modifier =
    shadow(elevation, shape, ambientColor = ambient, spotColor = spot)

/** Named SoftCard presets. One Hero per screen; List for repeated rows/tiles; InSheet = flat + hairline. */
enum class SoftCardStyle(val radius: Dp, val elevation: Dp, val ambient: Color, val spot: Color) {
    /** The single loud card on a screen (Discover paste card, empty-state card). */
    Hero(28.dp, SoftElevHero, SoftAmbientHero, SoftSpotHero),
    /** Repeated cards in lists/grids (recipe tiles, collection rows, form sections). */
    List(20.dp, SoftElevList, SoftAmbientList, SoftSpotList),
    /** Cards inside a sheet / already-elevated surface: no shadow, hairline border. */
    InSheet(20.dp, 0.dp, Color.Transparent, Color.Transparent),
    /** Collections grid tiles only (Design Lab #10): radius 24, List depth. */
    CollectionTile(24.dp, SoftElevList, SoftAmbientList, SoftSpotList),
}

/** True when the system "Animator duration scale" is off: press/heart springs snap instead of animating. */
@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }
}

/**
 * Soft press feedback (Design Lab #12): scale to [pressedScale] while [interaction] is pressed, spring with
 * StiffnessMediumLow, via graphicsLayer (no elevation jump). Snaps when animations are disabled.
 * Put it FIRST in the modifier chain so the shadow and background scale together.
 */
fun Modifier.softPressScale(interaction: InteractionSource, pressedScale: Float): Modifier = composed {
    val pressed by interaction.collectIsPressedAsState()
    val reduce = rememberReduceMotion()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = if (reduce) snap() else spring(stiffness = Spring.StiffnessMediumLow),
        label = "softPress",
    )
    graphicsLayer { scaleX = scale; scaleY = scale }
}

/**
 * Heart toggle bounce (Design Lab #12): when [favorite] flips, pop and settle on a spring with dampingRatio 0.5.
 * No animation on first composition or when animations are disabled. Apply via graphicsLayer scale.
 */
@Composable
fun rememberHeartScale(favorite: Boolean): Float {
    val reduce = rememberReduceMotion()
    val anim = remember { Animatable(1f) }
    val seen = remember { booleanArrayOf(false) }
    LaunchedEffect(favorite) {
        if (!seen[0]) { seen[0] = true; return@LaunchedEffect }
        if (reduce) return@LaunchedEffect
        anim.snapTo(if (favorite) 1.25f else 0.85f)
        anim.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium))
    }
    return anim.value
}

/** Press scale for tappable SoftCards / card-like rows: 0.98. */
const val SoftCardPressScale = 0.98f
/** Press scale for PillButton: 0.97. */
const val PillPressScale = 0.97f

/**
 * Soft white card: preset radius + gentle ambient shadow (or hairline in sheets), airy padding.
 * Pass [onClick] (not a clickable modifier) so the ripple is clipped to the card, the shadow is kept, and the card gets
 * the 0.98 press scale.
 */
@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    style: SoftCardStyle = SoftCardStyle.List,
    color: Color = MaterialTheme.colorScheme.surface,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    /** Per-instance shadow override (e.g. Hero ambient at List elev for a photo tile that reads flat). */
    ambient: Color = style.ambient,
    spot: Color = style.spot,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(style.radius)
    val interaction = remember { MutableInteractionSource() }
    val pressed = if (onClick != null) modifier.softPressScale(interaction, SoftCardPressScale) else modifier
    val base = if (style == SoftCardStyle.InSheet) {
        pressed.border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
    } else {
        pressed.softShadow(style.elevation, shape, ambient, spot)
    }
    val clickable = if (onClick != null) {
        Modifier.clip(shape).clickable(interactionSource = interaction, indication = LocalIndication.current, onClickLabel = onClickLabel, onClick = onClick)
    } else Modifier
    Column(base.background(color, shape).then(clickable).padding(contentPadding), content = content)
}

/** Hero preset: radius 28, elevation 8 (Hero tokens). Use at most once per screen. */
@Composable
fun SoftCardHero(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surface,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    content: @Composable ColumnScope.() -> Unit,
) = SoftCard(modifier, SoftCardStyle.Hero, color, contentPadding, content = content)

/** List preset: radius 20, elevation 4 (List tokens). */
@Composable
fun SoftCardList(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surface,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    content: @Composable ColumnScope.() -> Unit,
) = SoftCard(modifier, SoftCardStyle.List, color, contentPadding, content = content)

/** In-sheet preset: elevation 0 + outlineVariant hairline. */
@Composable
fun SoftCardInSheet(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surface,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    content: @Composable ColumnScope.() -> Unit,
) = SoftCard(modifier, SoftCardStyle.InSheet, color, contentPadding, content = content)

/** Borderless filled field on a soft chip background; no outline box, no floating label. */
@Composable
fun SoftField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
    shape: Shape = RoundedCornerShape(16.dp),
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    container: Color = MaterialTheme.colorScheme.surfaceContainer,
    isError: Boolean = false,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    TextField(
        value = value, onValueChange = onValueChange,
        modifier = modifier.heightIn(min = 48.dp),
        placeholder = { Text(placeholder, style = textStyle, maxLines = if (singleLine) 1 else Int.MAX_VALUE) },
        singleLine = singleLine, minLines = minLines, shape = shape, textStyle = textStyle,
        isError = isError, enabled = enabled,
        keyboardOptions = keyboardOptions, keyboardActions = keyboardActions,
        leadingIcon = leadingIcon, trailingIcon = trailingIcon,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = container, unfocusedContainerColor = container, disabledContainerColor = container,
            errorContainerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent, errorIndicatorColor = Color.Transparent,
        ),
    )
}

/**
 * Full-width SOLID Retro Green pill, white text (7.0:1). Colour role: the only solid-green
 * surface in the app — reserve it for the one primary CTA on a screen (Import, Start cooking, Next / Finish, Save).
 */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /** false = wrap content (compact inline action, e.g. the import pill's Review). */
    fill: Boolean = true,
    minHeight: Dp = 52.dp,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    /** Fill / label override. Default = Retro Green + white (Sai Tangerine palette); never Ink on Retro Green (2.5:1). */
    containerColor: Color = RetroGreen,
    contentColor: Color = Color.White,
) {
    val interaction = remember { MutableInteractionSource() }
    Button(
        onClick = onClick, enabled = enabled, interactionSource = interaction,
        modifier = Modifier.softPressScale(interaction, PillPressScale)
            .then(if (fill) modifier.fillMaxWidth() else modifier).heightIn(min = minHeight),
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(containerColor = containerColor, contentColor = contentColor),
        // Fill stays flat (elev 0) + 0.97 press scale; floating CTAs get their lift from the sticky bar (SoftElevFloat).
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
        contentPadding = contentPadding,
    ) {
        if (leadingIcon != null) {
            Icon(leadingIcon, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, maxLines = 1)
        if (trailingIcon != null) {
            Spacer(Modifier.width(6.dp))
            Icon(trailingIcon, null, Modifier.size(20.dp))
        }
    }
}

/** Small round badge (step numbers etc.): soft fill + deep text, never solid. */
@Composable
fun NumberBadge(
    n: Int,
    modifier: Modifier = Modifier,
    /** Fixed circle diameter (cook mode's large current-step badge); null = compact padded badge. */
    size: Dp? = null,
    textStyle: TextStyle = MaterialTheme.typography.labelLarge,
) {
    val base = if (size != null) modifier.size(size) else modifier
    Box(
        base.background(SoftPistachio, CircleShape)
            .then(if (size == null) Modifier.padding(horizontal = 10.dp, vertical = 5.dp) else Modifier),
        contentAlignment = Alignment.Center,
    ) { Text("$n", style = textStyle, color = Ink, fontWeight = FontWeight.Bold) }
}

/**
 * Bottom-sheet style surface (ReciMe "sheet over photo"): white, only the TOP corners rounded, light upward shadow,
 * fills the width of its slot. Depth A used a negative overlap onto a full-bleed hero; depth B (Detail) insets it 16dp
 * under a floating photo card with a 12dp gap and [bottomRadius] so the pair floats as a stack.
 */
@Composable
fun SoftSheet(
    modifier: Modifier = Modifier,
    radius: Dp = 30.dp,
    color: Color = MaterialTheme.colorScheme.surface,
    contentPadding: PaddingValues = PaddingValues(24.dp),
    /** Soft depth B: >0 rounds the bottom too, for an inset floating sheet that ends on the page ground (Detail). */
    bottomRadius: Dp = 0.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(topStart = radius, topEnd = radius, bottomStart = bottomRadius, bottomEnd = bottomRadius)
    Column(
        modifier
            .fillMaxWidth()
            .softShadow(SoftElevSheet, shape, SoftAmbientSheet, SoftSpotSheet)
            .background(color, shape)
            .padding(contentPadding),
        content = content,
    )
}

/**
 * Secondary pill: Soft Pistachio fill, Ink text by default, no outline. [fill] = full width; otherwise wraps
 * content. For secondary actions next to / under a [PillButton] (Open original, Prev, Add to grocery).
 * Pass [contentColor] = RetroGreenDeep for the "grocery strip" role (soft fill + deep text). Never a solid slab.
 */
@Composable
fun SoftOutlinePill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    fill: Boolean = false,
    container: Color = SoftPistachio,
    contentColor: Color = Ink,
    /** Optional hairline, e.g. RetroGreen for the Discover clipboard pill / cook-mode Previous (mocks 02, 05). */
    border: BorderStroke? = null,
) {
    Surface(
        onClick = onClick, enabled = enabled,
        shape = RoundedCornerShape(50), color = container, contentColor = contentColor, border = border,
        modifier = (if (fill) modifier.fillMaxWidth() else modifier).heightIn(min = 52.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 22.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        ) {
            val tint = if (enabled) contentColor else contentColor.copy(alpha = 0.38f)
            if (icon != null) {
                Icon(icon, null, Modifier.size(18.dp), tint = if (enabled) RetroGreenDeep else tint)
                Spacer(Modifier.width(8.dp))
            }
            Text(text, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, color = tint, maxLines = 1)
        }
    }
}

/** Alias: tonal (filled-soft) variant of the secondary pill. */
@Composable
fun SoftTonalButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null) =
    SoftOutlinePill(text, onClick, modifier, enabled, icon, fill = true)

/** Pill chip replacing Material FilterChip / AssistChip. Selected = Retro Green + white bold text (7.0:1); else Chip + Muted. */
@Composable
fun SoftChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    /** Selected chip gets a Retro Green hairline (segmented Ingredients | Steps switch, mock 04). */
    outlined: Boolean = false,
    /** Centre the label (when the chip is stretched with weight in a segmented switch). */
    center: Boolean = false,
    /** Unselected background; pass Transparent when the chip sits inside a tinted track. */
    unselectedColor: Color = ChipColor,
    /** Selected fill / label override. Default = Retro Green + white (Sai Tangerine palette); pass SoftPistachio + Ink for a soft select. */
    selectedColor: Color = RetroGreen,
    selectedContentColor: Color = Color.White,
) {
    val fg = if (selected) selectedContentColor else Muted
    Row(
        modifier
            .clip(CircleShape)
            .background(if (selected) selectedColor else unselectedColor)
            .then(if (outlined && selected) Modifier.border(1.5.dp, RetroGreen, CircleShape) else Modifier)
            .clickable(enabled = enabled, onClick = onClick)
            .heightIn(min = 36.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (center) androidx.compose.foundation.layout.Arrangement.Center else androidx.compose.foundation.layout.Arrangement.Start,
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(16.dp), tint = if (selected && selectedColor == SoftPistachio) RetroGreenDeep else fg)
            Spacer(Modifier.width(6.dp))
        }
        Text(
            label, style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            color = if (enabled) fg else fg.copy(alpha = 0.4f), maxLines = 2,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
    }
}
