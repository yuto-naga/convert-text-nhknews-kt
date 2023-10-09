package resources.types

enum class BracketType(val intValue: Int, val openValue: Char, val closeValue: Char) {
    かぎ括弧(1, '「', '」'),
    ;

    companion object {
        fun valueOf(value: Int): BracketType =
            BracketType.values().find { it.intValue == value }
                ?: throw IllegalArgumentException("No enum constant with intValue: $value")


        /** 文字列から引用符を探す */
        fun valueOfOpenValue(line: String): BracketType? {
            line.forEach { c ->
                return BracketType.values().find { it.openValue == c }
            }
            return null
        }

    }
}