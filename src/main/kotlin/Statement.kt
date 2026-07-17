package com.zxcjabka.game

data class PreparedStatement(
    val statement: String,
    val statementStatus: StatementStatus,
    val statementType: StatementType,
    val table: Table,
)

enum class StatementType {
    STATEMENT_INSERT, STATEMENT_SELECT, STATEMENT_UPDATE, STATEMENT_DELETE, STATEMENT_UNDEFINED
}

enum class StatementStatus {
    PREPARE_SUCCESS, PREPARE_UNDEFINED
}
