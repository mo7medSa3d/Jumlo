package com.jumlo.app.model

enum class CollisionPolicy(val labelEn: String, val labelAr: String) {
    KEEP_BOTH("Keep both (+1)", "الاحتفاظ بالاثنين (+1)"),
    SKIP("Skip", "تخطي"),
    REPLACE("Replace file", "استبدال الملف")
}
