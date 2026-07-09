package com.zxcjabka.game

import java.io.RandomAccessFile
import kotlin.system.exitProcess


fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("Must supply a database filename.")
        exitProcess(1)
    }
    val statementExecutor = StatementExecutor()
    val commandProcessor = CommandProcessor(statementExecutor)
    val table = dbOpen(args[0])
    while (true) {
        printUsage()
        val command = readlnOrNull() ?: "Undefined"
        commandProcessor.processCommand(command, table)

    }
}

fun dbOpen(filePath: String): Table {
    val file = RandomAccessFile(filePath, "rw")
    val pager = Pager(
        file = file,
    )
    if (pager.numPages == 0) {
        val rootNode = pager.getPage(0)
        initializeLeafNode(rootNode)
        setIsRoot(rootNode, true)
    }
    return Table(
        pager = pager,
        rootPageNum = 0
    )
}


fun printUsage() {
    println()
    print("db > ")
}

