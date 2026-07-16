package com.zxcjabka.game

import com.zxcjabka.game.StatementExecutor.*
import java.nio.ByteBuffer
import kotlin.system.exitProcess

class CommandProcessor(private val statementExecutor: StatementExecutor) {

    fun processCommand(command: String, table: Table) {
        if (command.startsWith(".")) {
            when (doMetaCommand(command, table)) {
                MetaCommands.META_COMMAND_SUCCESS -> return
                MetaCommands.META_COMMAND_UNDEFINED -> print("Undefined command $command")
            }
        } else {
            val statement = prepareStatement(command, table)
            when (statement.statementStatus) {
                StatementStatus.PREPARE_SUCCESS -> statementExecutor.executeStatement(statement)
                StatementStatus.PREPARE_UNDEFINED -> print("Undefined command $command")
            }

        }
    }

    private fun doMetaCommand(command: String, table: Table): MetaCommands {
        return when {
            command == ".q" || command == ".exit" -> {
                flush(table)
                exitProcess(0)
            }

            command.startsWith(".dump_page") -> {
                val pageNum = command.split(" ")[1].toInt()
                dumpPage(table, pageNum)
                MetaCommands.META_COMMAND_SUCCESS
            }

            command.startsWith(".dump_tree") -> {
                dumpTree(table, table.rootPageNum)
                MetaCommands.META_COMMAND_SUCCESS
            }

            else -> MetaCommands.META_COMMAND_UNDEFINED
        }
    }

    private fun prepareStatement(command: String, table: Table): PreparedStatement {

        when {
            command.startsWith("insert") -> {
                return PreparedStatement(
                    command,
                    statementStatus = StatementStatus.PREPARE_SUCCESS,
                    statementType = StatementType.STATEMENT_INSERT,
                    table = table
                )
            }

            command.startsWith("delete") -> {
                return PreparedStatement(
                    statement = command,
                    statementStatus = StatementStatus.PREPARE_SUCCESS,
                    statementType = StatementType.STATEMENT_DELETE,
                    table = table

                )
            }

            command.startsWith("select") -> {
                return PreparedStatement(
                    statement = command,
                    statementStatus = StatementStatus.PREPARE_SUCCESS,
                    statementType = StatementType.STATEMENT_SELECT,
                    table = table
                )
            }

            command.startsWith("update") -> {
                return PreparedStatement(
                    statement = command,
                    statementStatus = StatementStatus.PREPARE_SUCCESS,
                    statementType = StatementType.STATEMENT_UPDATE,
                    table = table
                )
            }

            else -> return PreparedStatement(
                statement = command,
                statementStatus = StatementStatus.PREPARE_UNDEFINED,
                statementType = StatementType.STATEMENT_UNDEFINED,
                table = table
            )
        }
    }


    enum class MetaCommands { META_COMMAND_SUCCESS, META_COMMAND_UNDEFINED }

}

fun dumpTree(
    table: Table,
    pageNum: Int,
    indent: String = "",
) {

    val page = table.pager.getPage(pageNum)

    println("$indent=============================================")
    println("$indent Page num: $pageNum")
    println("$indent Node type: ${getNodeType(page)}")
    println("$indent Is root: ${getIsRoot(page)}")
    println("$indent Parent: ${getParent(page)}")
    when (getNodeType(page)) {
        NodeType.NODE_INTERNAL -> {
            val keys = getInternalNodeNumKeys(page)
            println("$indent Num keys: $keys")
            repeat(keys) { i ->
                println("$indent Child[$i]: ${getInternalNodeChild(page, i)}  key=${getInternalNodeKey(page, i)}")
            }
            println("$indent Right child: ${getRightChild(page)}")
            println()
            for (i in 0 until keys) {
                println(
                    "page=$pageNum child[$i]=${getInternalNodeChild(page, i)}"
                )
                dumpTree(table, getInternalNodeChild(page, i), "$indent   ")
            }
            dumpTree(table, getRightChild(page), "$indent   ")
        }

        NodeType.NODE_LEAF -> {
            println("$indent Num cells: ${getLeafNodeNumCells(page)}")
            println("$indent Next leaf: ${getLeafNextLeafNode(page)}")
            print("$indent Keys: ")
            repeat(getLeafNodeNumCells(page)) { i ->
                print("${getNodeKeyValue(page, i)} ")
            }
            println()
            println("$indent Rows:")
            repeat(getLeafNodeNumCells(page)) { i ->
                println("$indent   ${getRow(page, i)}")
            }
        }
    }
    println("$indent=============================================")
}

fun dumpPage(table: Table, pageNum: Int) {
    val page = table.pager.getPage(pageNum)
    println("=============================================")
    println("Page num: $pageNum")
    println("Node type ${getNodeType(page)}")
    println("Is root ${getIsRoot(page)}")
    println("Parent node ${ByteBuffer.wrap(page).getInt(PARENT_POINTER_OFFSET)}")

    when (getNodeType(page)) {
        NodeType.NODE_LEAF -> dumpLeaf(page)
        NodeType.NODE_INTERNAL -> dumpInternal(page)
    }
    println("=============================================")
}

fun dumpInternal(page: ByteArray) {
    println("Num keys ${getInternalNodeNumKeys(page)}")
    repeat(getInternalNodeNumKeys(page)) { i ->
        println("Child: ${getInternalNodeChild(page, i)}")
        println("Key: ${getInternalNodeKey(page, i)}")
    }
}

fun dumpLeaf(page: ByteArray) {
    println("Num cells ${getLeafNodeNumCells(page)}")
    println("Next leaf ${getLeafNextLeafNode(page)}")
    repeat(getLeafNodeNumCells(page)) { i ->
        val row = getRow(page, i)
        println("Row: $row")
    }
}

fun getRow(page: ByteArray, cell: Int): Row {
    val username = ByteArray(USERNAME_SIZE)
    val email = ByteArray(EMAIL_SIZE)

    val valueOffset = leafNodeCell(cell) + LEAF_NODE_KEY_SIZE

    val id = ByteBuffer.wrap(page).getInt(valueOffset)
    System.arraycopy(
        page,
        valueOffset + USERNAME_OFFSET,
        username,
        0,
        USERNAME_SIZE
    )
    System.arraycopy(
        page,
        valueOffset + EMAIL_OFFSET,
        email,
        0,
        EMAIL_SIZE
    )
    return Row(
        Id = id,
        Username = String(username).trimEnd('\u0000'),
        Email = String(email).trimEnd('\u0000')
    )
}