package com.zxcjabka.game

abstract class StatementException(
    val statement: String,
    message: String
) : RuntimeException(message){
    override fun toString(): String{
        return """
            Exception at $statement
            message: $message
        """.trimIndent()
    }
}

class IdNotFoundException(statement: String,id: Int) : StatementException(statement = statement, message = "Not found id: $id")

class InvalidPreparedStatementException(statement: String) : StatementException(statement,  message = "Incorrect prepared statement")

class IdAlreadyExists(statement:String) : StatementException(statement,  message = "Id already exists")