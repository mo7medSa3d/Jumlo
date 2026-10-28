# AUDIT_2 Phase 0 production Kotlin/Java lexical scan

**Base:** `d599b8a3159f38623e3d0b83c3d58a23073e70cf`. Direct GitHub contents API read. This is a **candidate inventory**, not runtime qualification. Matches inside token definitions can be intentional; no match is a substitute for behavior testing. **INCOMPLETE: 14/244 files**.

## Batch 0–13

Read 14 files; fetch failures 0; candidates 0.



## Batch 14–27

Files attempted: 14, failures: 0, candidates: 0.


## Batch 28–41

Files attempted: 14, failures: 0, candidates: 0.


## Batch 42–55

Files attempted: 14, failures: 0, candidates: 0.


## Batch 56–69

Files attempted: 14, failures: 0, candidates: 0.


## Batch 70–83

Files attempted: 14, failures: 0, candidates: 4.

- **COLOR** `app/src/main/java/com/jumlo/app/printing/PrintScreen.kt:526`: `.background(Color.White),`
- **TEXT** `app/src/main/java/com/jumlo/app/printing/PrintScreen.kt:538`: `Text("${page.rotationDegrees}°", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary)`
- **COLOR** `app/src/main/java/com/jumlo/app/printing/PrintScreen.kt:637`: `}},contentAlignment=Alignment.Center) {Box(Modifier.size(12.dp).background(accent,CircleShape).border(2.dp,Color.White,CircleShape))}`
- **COLOR** `app/src/main/java/com/jumlo/app/printing/PrintScreen.kt:661`: `Box(modifier.background(Color.White),contentAlignment=Alignment.Center) {`

## Batch 84–97

Files attempted: 14, failures: 0, candidates: 9.

- **COLOR** `app/src/main/java/com/jumlo/app/ui/components/JumloSplash.kt:98`: `Color(0xFF0C1231), Color(0xFF231D66), Color(0xFF4434A9)`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/components/JumloSplash.kt:106`: `Brush.radialGradient(listOf(Color(0xFF6B61EB).copy(alpha = .37f), Color.Transparent)),`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/components/JumloSplash.kt:113`: `Brush.radialGradient(listOf(Color(0xFF3EABD0).copy(alpha = .16f), Color.Transparent)),`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/components/JumloSplash.kt:124`: `.background(Color(0xFF88F5D1).copy(alpha = breath + .37f), CircleShape)`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/components/JumloSplash.kt:128`: `.size(5.dp).background(Color.White.copy(alpha = .25f), CircleShape)`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/components/JumloSplash.kt:142`: `.background(Brush.linearGradient(listOf(Color(0xFF6452E8), Color(0xFF8F68F4)))),`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/components/JumloSplash.kt:156`: `color = Color.White`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/components/JumloSplash.kt:160`: `.background(Color(0xFF81F2CE), CircleShape)`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/components/JumloSplash.kt:166`: `color = Color(0xFFE0E5FF), textAlign = TextAlign.Center`

## Batch 98–111

Files attempted: 14, failures: 0, candidates: 1.

- **TEXT** `app/src/main/java/com/jumlo/app/ui/downloads/MediaWorkspace.kt:400`: `label={Text("$kbps kbps",style=QuietTheme.typography.metadataStrong)}`

## Batch 112–125

Files attempted: 14, failures: 0, candidates: 6.

- **COLOR** `app/src/main/java/com/jumlo/app/ui/icons/Icons.kt:24`: `fill = SolidColor(Color.Black), fillAlpha = alpha,`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/scan/ScanScreen.kt:95`: `Box(Modifier.fillMaxWidth().height(200.dp).clip(QuietTheme.shapes.thumbnail).background(Color(0xFFF4F4F4))) {`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/scan/ScanScreen.kt:99`: `if ((row + column) % 2 == 0) drawRect(Color(0xFFE2E2E2), Offset(column * cell, row * cell), androidx.compose.ui.geometry.Size(cell, cell))`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/scan/ScanScreen.kt:152`: `listOf(Color.Black, Color(0xFF1746A2), Color(0xFF8E2430), Color(0xFF145D4C)).forEachIndexed { index, ink ->`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/scan/ScanScreen.kt:156`: `Box(contentAlignment = Alignment.Center) { if (inkArgb == ink.toArgb()) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(22.dp)) }`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/scan/ScanScreen.kt:194`: `Surface(onClick = { if (!busy) onSaved(file) }, shape = QuietTheme.shapes.thumbnail, color = Color.White,`
