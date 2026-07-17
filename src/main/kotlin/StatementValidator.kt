package com.zxcjabka.game

class StatementValidator {
    companion object {
        fun validate(statement: PreparedStatement) {
            when (statement.statementType) {
                StatementType.STATEMENT_SELECT -> validateSelect(statement)
                StatementType.STATEMENT_INSERT -> validateInsert(statement)
                StatementType.STATEMENT_UPDATE -> validateUpdate(statement)
                StatementType.STATEMENT_DELETE -> validateDelete(statement)
                StatementType.STATEMENT_UNDEFINED -> return
            }
        }

        private fun validateDelete(statement: PreparedStatement) {
            val id = getIdFromDeleteStatement(statement)
            val cursor = tableFind(statement.table, id)
            if (!cursor.existsById(id)) throw IdNotFoundException(statement.statement, id)
        }

        private fun getIdFromDeleteStatement(statement: PreparedStatement): Int {
            val statementParts = statement.statement.getParts(" ")
            if (statementParts.size < 2) {
                throw InvalidPreparedStatementException(statement.statement)
            }
            when (val id = statementParts[1].toIntOrNull()) {
                null -> throw InvalidPreparedStatementException(statement.statement)
                else -> return id
            }
        }

        private fun String.getParts(delimiter: String): List<String> {
            return this.split(delimiter).filter { it.isNotBlank() }
        }

        private fun validateUpdate(statement: PreparedStatement) {
            val parts = statement.statement.getParts("set")
            if (parts.size < 2) throw InvalidPreparedStatementException(statement.statement)

            val id = parts[0].split(" ").filter { it != "update" }[0].toIntOrNull()
            if (id == null) throw InvalidPreparedStatementException(statement.statement)

            val values = mutableMapOf<String, String>()

            for (token in parts[1].getParts(" ")) {

                if (token == "values") continue

                val pair = token.split("=")

                if (pair.size != 2)
                    throw InvalidPreparedStatementException(statement.statement)

                values[pair[0]] = pair[1]
            }

            if (values.isEmpty()) throw InvalidPreparedStatementException(statement.statement)

            val cursor = tableFind(statement.table, id)
            if (!cursor.existsById(id)) throw IdNotFoundException(statement.statement, id)
        }

        private fun validateInsert(statement: PreparedStatement) {
            val parts = statement.statement.getParts("insert")[0].split(" ").filter { it.isNotBlank() }
            if (parts.size != 3) throw InvalidPreparedStatementException(statement.statement)
            val id = parts[0].toIntOrNull() ?: throw InvalidPreparedStatementException(statement.statement)
            val cursor = tableFind(statement.table, id)
            if (cursor.existsById(id)) throw IdAlreadyExists(statement.statement)
        }

        private fun validateSelect(statement: PreparedStatement) {
            if (statement.statement != "select") throw InvalidPreparedStatementException(statement.statement)
        }
    }
}