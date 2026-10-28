# AUDIT_2 Phase 0 production Kotlin/Java lexical scan

**Base:** `d599b8a3159f38623e3d0b83c3d58a23073e70cf`. Direct GitHub contents API read. This is a **candidate inventory**, not runtime qualification. Matches inside token definitions can be intentional; no match is a substitute for behavior testing. **COMPLETE for the declared lexical rules: 244/244 production Kotlin/Java files, zero failed fetches.** This is not a semantic UI/operation audit and does not include XML, shell/Python or generated binaries.

**Aggregate matched candidates:** 0 TODO/FIXME/stub-marker locations; 0 syntactically empty callbacks; **204** direct color references (mostly intentionally centralized tokens); **7** direct `Text("…")`/contentDescription literals (most are numeric values, symbols or source filenames). Every match is listed below with the exact source file and line. Multiline string construction and semantically inert nonempty callbacks remain outside this lexical detector.

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

## Batch 126–139

Files attempted: 14, failures: 0, candidates: 159.

- **COLOR** `app/src/main/java/com/jumlo/app/ui/settings/AccentPickerDialog.kt:40`: `Triple("BLUE", R.string.redesign_accent_blue, Color(0xFF2F46C6)),`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/settings/AccentPickerDialog.kt:41`: `Triple("VIOLET", R.string.redesign_accent_violet, Color(0xFF6942B0)),`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/settings/AccentPickerDialog.kt:42`: `Triple("TEAL", R.string.redesign_accent_teal, Color(0xFF086F6B)),`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/settings/AccentPickerDialog.kt:43`: `Triple("CORAL", R.string.redesign_accent_coral, Color(0xFFB63E54)),`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/settings/AppearanceColorPicker.kt:68`: `val seed = M3ColorFactory.hexColor(hexDraft) ?: Color(0xFF1E88E5)`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/settings/AppearanceColorPicker.kt:167`: `val scheme = M3ColorFactory.scheme(M3ColorFactory.hexColor(preset.hex) ?: Color.Gray, dark = false)`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/settings/AppearanceColorPicker.kt:182`: `M3ColorFactory.hexColor(preset.hex) ?: Color.Gray, RoundedCornerShape(6.dp)`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:14`: `val LightBackground = Color(0xFFF6F6F8)`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:15`: `val LightSurface = Color(0xFFFFFFFF)`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:16`: `val LightSurfaceDim = Color(0xFFEDEDEF)`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:17`: `val LightSurfaceBright = Color(0xFFFFFFFF)`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:18`: `val LightSurfaceContainerLowest = Color(0xFFFFFFFF)`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:19`: `val LightSurfaceContainerLow = Color(0xFFFFFFFF)`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:20`: `val LightSurfaceContainer = Color(0xFFF0F0F3)`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:21`: `val LightSurfaceContainerHigh = Color(0xFFE8E8ED)`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:22`: `val LightSurfaceContainerHighest = Color(0xFFDFDFE5)`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:23`: `val LightOnSurface = Color(0xFF202024)`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:24`: `val LightOnSurfaceVariant = Color(0xFF55555E)`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:25`: `val LightOutline = Color(0xFF777781)`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:26`: `val LightOutlineVariant = Color(0xFFDDDEE5)`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:27`: `val LightPrimary = Color(0xFF2F46C6)`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:28`: `val LightPrimaryButton = Color(0xFF2F46C6)`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:29`: `val LightOnPrimary = Color(0xFFFFFFFF)`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:30`: `val LightPrimaryContainer = Color(0xFFE5E9FF)`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:31`: `val LightOnPrimaryContainer = Color(0xFF19266C)`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:32`: `val LightPrimaryFixed = Color(0xFFE5E9FF)`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:33`: `val LightPrimaryFixedDim = Color(0xFFC3CEFF)`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:34`: `val LightOnPrimaryFixed = Color(0xFF16246E)`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:35`: `val LightOnPrimaryFixedVariant = Color(0xFF35469D)`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:36`: `val LightSecondary = Color(0xFF276D77)`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:37`: `val LightOnSecondary = Color(0xFFFFFFFF)`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:38`: `val LightSecondaryContainer = Color(0xFFDBF5F2)`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:39`: `val LightOnSecondaryContainer = Color(0xFF124F52)`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:40`: `val LightTertiary = Color(0xFF7050AB)`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:41`: `val LightOnTertiary = Color(0xFFFFFFFF)`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:42`: `val LightTertiaryContainer = Color(0xFFF0E8FC)`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:43`: `val LightOnTertiaryContainer = Color(0xFF38216B)`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:44`: `val LightError = Color(0xFFBA334A)`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:45`: `val LightOnError = Color(0xFFFFFFFF)`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:46`: `val LightErrorContainer = Color(0xFFFFE5E8)`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:47`: `val LightOnErrorContainer = Color(0xFF781D32)`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:48`: `val LightCardBorder = Color(0xFFDDDEE5)`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:51`: `val DarkBackground = Color(0xFF000000)`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:52`: `val DarkSurface = Color(0xFF101010)`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:53`: `val DarkSurfaceDim = Color(0xFF000000)`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:54`: `val DarkSurfaceBright = Color(0xFF353535)`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:55`: `val DarkSurfaceContainerLowest = Color(0xFF000000)`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:56`: `val DarkSurfaceContainerLow = Color(0xFF151515)`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:57`: `val DarkSurfaceContainer = Color(0xFF1D1D1D)`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:58`: `val DarkSurfaceContainerHigh = Color(0xFF262626)`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:59`: `val DarkSurfaceContainerHighest = Color(0xFF303030)`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:60`: `val DarkOnSurface = Color(0xFFF5F5F5)`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:61`: `val DarkOnSurfaceVariant = Color(0xFFC4C4C4)`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:62`: `val DarkOutline = Color(0xFF969696)`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:63`: `val DarkOutlineVariant = Color(0xFF3D3D3D)`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:64`: `val DarkPrimary = Color(0xFFA9B7FF)`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:65`: `val DarkPrimaryButton = Color(0xFFA9B7FF)`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:66`: `val DarkOnPrimary = Color(0xFF13205A)`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:67`: `val DarkPrimaryContainer = Color(0xFF29376E)`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:68`: `val DarkOnPrimaryContainer = Color(0xFFE5E9FF)`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:69`: `val DarkPrimaryFixed = Color(0xFFE0E5FF)`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:70`: `val DarkPrimaryFixedDim = Color(0xFFB8C4FF)`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:71`: `val DarkOnPrimaryFixed = Color(0xFF111D5A)`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:72`: `val DarkOnPrimaryFixedVariant = Color(0xFF293B91)`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:73`: `val DarkSecondary = Color(0xFF92DCD8)`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:74`: `val DarkOnSecondary = Color(0xFF083C41)`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:75`: `val DarkSecondaryContainer = Color(0xFF164D50)`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:76`: `val DarkOnSecondaryContainer = Color(0xFFDCF9F5)`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:77`: `val DarkTertiary = Color(0xFFD3B7FC)`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:78`: `val DarkOnTertiary = Color(0xFF361D65)`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:79`: `val DarkTertiaryContainer = Color(0xFF4C336D)`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:80`: `val DarkOnTertiaryContainer = Color(0xFFF0E4FF)`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:81`: `val DarkError = Color(0xFFFFB5C1)`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:82`: `val DarkOnError = Color(0xFF690D26)`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:83`: `val DarkErrorContainer = Color(0xFF72273C)`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:84`: `val DarkOnErrorContainer = Color(0xFFFFE1E7)`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:85`: `val DarkCardBorder = Color(0xFF3D3D3D)`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:126`: `pdfPrimary = Color(0xFFBC3555),`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:127`: `pdfContainer = Color(0xFFFFE7EC),`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:128`: `pdfOnContainer = Color(0xFF7E2340),`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:129`: `pdfSubtitle = Color(0xFF922744),`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:130`: `imagesPrimary = Color(0xFFA76218),`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:131`: `imagesContainer = Color(0xFFFFF1D6),`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:132`: `imagesOnContainer = Color(0xFF78350F),`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:133`: `imagesSubtitle = Color(0xFF92400E),`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:134`: `downloadsPrimary = Color(0xFF087E77),`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:135`: `downloadsContainer = Color(0xFFDDF8F3),`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:136`: `downloadsOnContainer = Color(0xFF115E59),`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:137`: `downloadsSubtitle = Color(0xFF087E77),`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:138`: `filesPrimary = Color(0xFF2F46C6),`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:139`: `filesContainer = Color(0xFFE5E9FF),`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:140`: `filesOnContainer = Color(0xFF19266C),`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:141`: `filesSubtitle = Color(0xFF2F46C6),`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:142`: `vaultPrimary = Color(0xFF7152B3),`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:143`: `vaultContainer = Color(0xFFF0E8FC),`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:144`: `successPrimary = Color(0xFF15803D),`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:145`: `successContainer = Color(0xFFDCFCE7),`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:146`: `warningPrimary = Color(0xFFA76218),`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:147`: `warningContainer = Color(0xFFFFF1D6),`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:148`: `cardSurface = Color(0xFFFFFFFF),`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:149`: `cardBorder = Color(0xFFDDDEE5),`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:150`: `primaryActionBackground = Color(0xFF202024),`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:151`: `primaryActionText = Color(0xFFFFFFFF),`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:152`: `dockSurface = Color(0xF7FFFFFF),`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:153`: `dockBorder = Color(0x1A0F172A),`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:154`: `activePillContainer = Color(0xFFDDDEE5),`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:155`: `activePillContent = Color(0xFF202024)`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:159`: `pdfPrimary = Color(0xFFFFB5C1),`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:160`: `pdfContainer = Color(0xFF4C0519),`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:161`: `pdfOnContainer = Color(0xFFFFE7EC),`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:162`: `pdfSubtitle = Color(0xFFFECDD3),`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:163`: `imagesPrimary = Color(0xFFFCD34D),`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:164`: `imagesContainer = Color(0xFF451A03),`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:165`: `imagesOnContainer = Color(0xFFFFF1D6),`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:166`: `imagesSubtitle = Color(0xFFFDE68A),`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:167`: `downloadsPrimary = Color(0xFF92DCD8),`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:168`: `downloadsContainer = Color(0xFF042F2E),`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:169`: `downloadsOnContainer = Color(0xFFDDF8F3),`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:170`: `downloadsSubtitle = Color(0xFF99F6E4),`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:171`: `filesPrimary = Color(0xFF93C5FD),`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:172`: `filesContainer = Color(0xFF172554),`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:173`: `filesOnContainer = Color(0xFFE5E9FF),`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:174`: `filesSubtitle = Color(0xFFBFDBFE),`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:175`: `vaultPrimary = Color(0xFFC4B5FD),`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:176`: `vaultContainer = Color(0xFF2E1065),`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:177`: `successPrimary = Color(0xFF4ADE80),`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:178`: `successContainer = Color(0xFF052E16),`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:179`: `warningPrimary = Color(0xFFFCD34D),`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:180`: `warningContainer = Color(0xFF451A03),`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:181`: `cardSurface = Color(0xFF151515),`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:182`: `cardBorder = Color(0xFF3D3D3D),`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:183`: `primaryActionBackground = Color(0xFFDDDEE5),`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:184`: `primaryActionText = Color(0xFF202024),`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:185`: `dockSurface = Color(0xFF1D1D1D),`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:186`: `dockBorder = Color(0x26FFFFFF),`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:187`: `activePillContainer = Color(0xFF303030),`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:188`: `activePillContent = Color(0xFFF8FAFC)`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/M3ColorFactory.kt:54`: `val towardWhite = contrast(bg, Color.White) >= contrast(bg, Color.Black)`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/M3ColorFactory.kt:55`: `val target = if (towardWhite) Color.White else Color.Black`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/M3ColorFactory.kt:74`: `"TEAL" -> Color(0xFF00897B)`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/M3ColorFactory.kt:75`: `"CORAL" -> Color(0xFFE64A3A)`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/M3ColorFactory.kt:76`: `"VIOLET" -> Color(0xFF7B4DFF)`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/M3ColorFactory.kt:77`: `else -> Color(0xFF1E88E5)`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/M3ColorFactory.kt:140`: `background = Color.White,`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/M3ColorFactory.kt:142`: `surface = Color(0xFFFCFCFD),`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/M3ColorFactory.kt:144`: `surfaceVariant = Color(0xFFE0E2EC),`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/M3ColorFactory.kt:145`: `onSurfaceVariant = Color(0xFF44474F),`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/M3ColorFactory.kt:146`: `outline = Color(0xFF74777F),`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/M3ColorFactory.kt:147`: `outlineVariant = Color(0xFFC4C6D0),`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/M3ColorFactory.kt:151`: `val surface = if (amoled) Color.Black else Color(0xFF121316)`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/M3ColorFactory.kt:162`: `onPrimary = ensureContrast(primary, Color.White),`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/M3ColorFactory.kt:166`: `onSecondary = ensureContrast(secondary, Color.White),`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/M3ColorFactory.kt:170`: `onTertiary = ensureContrast(tertiary, Color.White),`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/M3ColorFactory.kt:174`: `onError = ensureContrast(error, Color.White),`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/M3ColorFactory.kt:176`: `onErrorContainer = ensureContrast(errorContainer, Color(0xFFFFDAD6)),`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/M3ColorFactory.kt:181`: `surfaceVariant = Color(0xFF49454F),`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/M3ColorFactory.kt:182`: `onSurfaceVariant = Color(0xFFCAC4D0),`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/M3ColorFactory.kt:183`: `outline = Color(0xFF938F99),`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/M3ColorFactory.kt:184`: `outlineVariant = Color(0xFF63606A),`

## Batch 140–153

Files attempted: 14, failures: 0, candidates: 23.

- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/ToolPalette.kt:13`: `Color(0xFFB63557), // rose: PDF`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/ToolPalette.kt:14`: `Color(0xFFAC641D), // amber: images`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/ToolPalette.kt:15`: `Color(0xFF087A73), // lagoon: transfers`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/ToolPalette.kt:16`: `Color(0xFF2F46C6), // indigo: documents`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/ToolPalette.kt:17`: `Color(0xFF7150A9), // iris: organizing`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/ToolPalette.kt:18`: `Color(0xFF326E99)  // ocean: utilities`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/ToolPalette.kt:21`: `Color(0xFFFF9DB3),`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/ToolPalette.kt:22`: `Color(0xFFFFCE88),`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/ToolPalette.kt:23`: `Color(0xFF82DED2),`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/ToolPalette.kt:24`: `Color(0xFFACB9FF),`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/ToolPalette.kt:25`: `Color(0xFFD3B7FC),`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/theme/ToolPalette.kt:26`: `Color(0xFF9CCDF0)`
- **TEXT** `app/src/main/java/com/jumlo/app/ui/viewer/PdfViewerScreen.kt:394`: `TextButton(onClick = ::reset, enabled = scale > 1.01f, modifier = Modifier.heightIn(min = 48.dp)) { Text("${(scale * 100).toInt()}% · $fitLabel") }`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/viewer/PdfViewerScreen.kt:397`: `.clip(QuietTheme.shapes.thumbnail).background(if (dark) Color.Black else Color.White).clipToBounds()`
- **TEXT** `app/src/main/java/com/jumlo/app/ui/viewer/PdfViewerScreen.kt:436`: `Text("${page + 1}", style = MaterialTheme.typography.labelMedium)`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/workspace/AnnotationToolbar.kt:51`: `Box(contentAlignment=Alignment.Center) {if(chosen) Text("✓",color=if(hex=="#FFEB3B") Color.Black else Color.White)}`
- **TEXT** `app/src/main/java/com/jumlo/app/ui/workspace/AnnotationToolbar.kt:51`: `Box(contentAlignment=Alignment.Center) {if(chosen) Text("✓",color=if(hex=="#FFEB3B") Color.Black else Color.White)}`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/workspace/ImageComparisonPreview.kt:57`: `drawLine(Color.Black.copy(alpha=.35f),Offset(x,0f),Offset(x,size.height),5.dp.toPx())`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/workspace/ImageComparisonPreview.kt:58`: `drawLine(Color.White,Offset(x,0f),Offset(x,size.height),2.dp.toPx())`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/workspace/ImageComparisonPreview.kt:59`: `drawCircle(Color.White,14.dp.toPx(),Offset(x,size.height/2))`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/workspace/ImageComparisonPreview.kt:60`: `drawLine(Color.Black,Offset(x-4.dp.toPx(),size.height/2),Offset(x+4.dp.toPx(),size.height/2),2.dp.toPx())`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/workspace/ImageComparisonPreview.kt:63`: `Text(stringResource(R.string.studio_before),Modifier.background(Color.Black.copy(alpha=.7f),QuietTheme.shapes.thumbnail).padding(6.dp),color=Color.White,style=Q`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/workspace/ImageComparisonPreview.kt:64`: `Text(stringResource(R.string.studio_after),Modifier.background(Color.Black.copy(alpha=.7f),QuietTheme.shapes.thumbnail).padding(6.dp),color=Color.White,style=Qu`

## Batch 154–167

Files attempted: 14, failures: 0, candidates: 1.

- **COLOR** `app/src/main/java/com/jumlo/app/ui/workspace/ImagePdfPreview.kt:135`: `drawRect(Color.White,Offset(x,y),Size(w,h))`

## Batch 168–181

Files attempted: 14, failures: 0, candidates: 8.

- **TEXT** `app/src/main/java/com/jumlo/app/ui/workspace/SourceOrderStrip.kt:84`: `Text("${index+1}. ${source.displayName}",maxLines=2,overflow=TextOverflow.Ellipsis,style=QuietTheme.typography.metadata)`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/workspace/VisualDocumentEditor.kt:381`: `val shade=Color.Black.copy(alpha=.48f)`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/workspace/VisualDocumentEditor.kt:390`: `drawLine(Color.White.copy(alpha=.5f),`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/workspace/VisualDocumentEditor.kt:393`: `drawLine(Color.White.copy(alpha=.5f),`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/workspace/VisualDocumentEditor.kt:445`: `drawCircle(Color.White,11.dp.toPx(),center=corner)`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/workspace/VisualImageSettings.kt:193`: `drawRoundRect(Color.White,Offset((size.width-w)/2,0f),Size(w,h),androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()))`
- **COLOR** `app/src/main/java/com/jumlo/app/ui/workspace/VisualWatermarkEditor.kt:146`: `Box(contentAlignment=Alignment.Center) {if(chosen) Icon(Icons.Rounded.Check,null,tint=if(hex=="#FFFFFF") Color.Black else Color.White)}`
- **TEXT** `app/src/main/java/com/jumlo/app/ui/workspace/VisualWatermarkEditor.kt:158`: `items(listOf(-90,-45,0,45,90)) {angle->FilterChip(border=null,selected=rotation==angle,onClick={edit {it.copy(text4=angle.toString())}},label={Text("$angle°")})`

## Batch 182–195

Files attempted: 14, failures: 0, candidates: 0.


## Batch 196–209

Files attempted: 14, failures: 0, candidates: 0.


## Batch 210–223

Files attempted: 14, failures: 0, candidates: 0.


## Batch 224–237

Files attempted: 14, failures: 0, candidates: 0.


## Batch 238–243

Files attempted: 6, failures: 0, candidates: 0.


## Coverage summary

Completed source-text lexical scan **244/244 production Kotlin/Java files** at the pinned base commit with 0 fetch failures in the final batch; prior batches also recorded zero fetch failures. This does not cover every resource XML, Gradle, shell/Python utility or generated binary. It cannot detect callbacks implemented as semantically dead branches, and it does not prove feature behavior. All candidate locations are retained above with paths and line numbers. Human review must separate theme token definitions, purposefully white document paper/overlays and actual hard-coded presentation styling.

