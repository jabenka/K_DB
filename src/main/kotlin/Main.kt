package com.zxcjabka.game

import java.io.RandomAccessFile
import java.nio.ByteBuffer
import kotlin.system.exitProcess

const val COLUMN_USERNAME: Int = 32
const val COLUMN_EMAIL: Int = 255
const val ID_SIZE: Int = Int.SIZE_BYTES
const val USERNAME_SIZE: Int = COLUMN_USERNAME
const val EMAIL_SIZE: Int = COLUMN_EMAIL
const val ID_OFFSET: Int = 0
const val USERNAME_OFFSET: Int = ID_OFFSET + ID_SIZE
const val EMAIL_OFFSET: Int = USERNAME_SIZE + USERNAME_OFFSET
const val ROW_SIZE: Int = EMAIL_OFFSET + EMAIL_SIZE
const val PAGE_SIZE: Int = 4096
const val TABLE_MAX_PAGES: Int = 100
const val ROWS_PER_PAGE = PAGE_SIZE / ROW_SIZE
const val HEADER_SIZE = Int.SIZE_BYTES

const val NODE_TYPE_SIZE = Byte.SIZE_BYTES
const val NODE_TYPE_OFFSET = 0

const val IS_ROOT_SIZE = Byte.SIZE_BYTES
const val IS_ROOT_OFFSET = NODE_TYPE_OFFSET + NODE_TYPE_SIZE

const val PARENT_POINTER_SIZE = Int.SIZE_BYTES
const val PARENT_POINTER_OFFSET = IS_ROOT_OFFSET + IS_ROOT_SIZE

const val COMMON_NODE_HEADER_SIZE = NODE_TYPE_SIZE + IS_ROOT_SIZE + PARENT_POINTER_SIZE

const val LEAF_NODE_NUM_CELLS_SIZE = Int.SIZE_BYTES
const val LEAF_NODE_NUM_CELLS_OFFSET = COMMON_NODE_HEADER_SIZE

const val LEAF_NODE_NEXT_LEAF_SIZE = Int.SIZE_BYTES

const val LEAF_NODE_NEXT_LEAF_OFFSET =
    LEAF_NODE_NUM_CELLS_OFFSET + LEAF_NODE_NUM_CELLS_SIZE

const val LEAF_NODE_HEADER_SIZE =
    COMMON_NODE_HEADER_SIZE +
            LEAF_NODE_NUM_CELLS_SIZE +
            LEAF_NODE_NEXT_LEAF_SIZE

const val LEAF_NODE_KEY_SIZE = Int.SIZE_BYTES
const val LEAF_NODE_KEY_OFFSET = 0
const val LEAF_NODE_VALUE_SIZE = ROW_SIZE
const val LEAF_NODE_VALUE_OFFSET = LEAF_NODE_KEY_OFFSET + LEAF_NODE_KEY_SIZE
const val LEAF_NODE_CELL_SIZE = LEAF_NODE_VALUE_SIZE + LEAF_NODE_VALUE_OFFSET
const val LEAF_NODE_SPACE_FOR_CELLS = PAGE_SIZE - LEAF_NODE_HEADER_SIZE
const val LEAF_NODE_MAX_CELLS = LEAF_NODE_SPACE_FOR_CELLS / LEAF_NODE_CELL_SIZE
const val LEAF_NODE_RIGHT_SPLIT_COUNT: Int = (LEAF_NODE_MAX_CELLS + 1) / 2
const val LEAF_NODE_LEFT_SPLIT_COUNT = (LEAF_NODE_MAX_CELLS + 1) - LEAF_NODE_RIGHT_SPLIT_COUNT

const val INTERNAL_NODE_NUM_KEYS_SIZE = Int.SIZE_BYTES
const val INTERNAL_NODE_NUM_KEYS_OFFSET = COMMON_NODE_HEADER_SIZE

const val INTERNAL_NODE_RIGHT_CHILD_SIZE = Int.SIZE_BYTES
const val INTERNAL_NODE_RIGHT_CHILD_OFFSET =
    INTERNAL_NODE_NUM_KEYS_OFFSET + INTERNAL_NODE_NUM_KEYS_SIZE

const val INTERNAL_NODE_HEADER_SIZE =
    COMMON_NODE_HEADER_SIZE +
            INTERNAL_NODE_NUM_KEYS_SIZE +
            INTERNAL_NODE_RIGHT_CHILD_SIZE
const val INTERNAL_NODE_KEY_SIZE = Int.SIZE_BYTES
const val INTERNAL_NODE_CHILD_SIZE = Int.SIZE_BYTES
const val INVALID_PAGE_NUM = Int.MAX_VALUE

const val INTERNAL_NODE_CELL_SIZE =
    INTERNAL_NODE_CHILD_SIZE +
            INTERNAL_NODE_KEY_SIZE

const val INTERNAL_NODE_MAX_CELLS = 3

fun initializeLeafNode(page: ByteArray) {
    setNodeType(page, NodeType.NODE_LEAF)
    setIsRoot(page, false)
    setLeafNodeNumCells(page, 0)
    setLeafNextLeafNode(page, 0)
}

fun initializeInternalNode(page: ByteArray) {
    setNodeType(page, NodeType.NODE_INTERNAL)
    setIsRoot(page, false)
    setInternalNodeNumKeys(page, 0)
    setRightChild(page, INVALID_PAGE_NUM)
}

fun setLeafNodeNumCells(page: ByteArray, numCells: Int) {
    ByteBuffer.wrap(page).putInt(LEAF_NODE_NUM_CELLS_OFFSET, numCells)
}

fun setIsRoot(page: ByteArray, isRoot: Boolean) {
    page[IS_ROOT_OFFSET] = if (isRoot) 1 else 0
}

fun setNodeType(page: ByteArray, nodeLeaf: NodeType) {
    val nodeLeafValue: Byte = when (nodeLeaf) {
        NodeType.NODE_INTERNAL -> 0
        NodeType.NODE_LEAF -> 1
    }
    ByteBuffer.wrap(page).put(NODE_TYPE_OFFSET, nodeLeafValue)
}

fun getNodeType(page: ByteArray): NodeType {
    return when (page[NODE_TYPE_OFFSET].toInt()) {
        0 -> NodeType.NODE_INTERNAL
        else -> NodeType.NODE_LEAF
    }
}

fun getIsRoot(page: ByteArray): Boolean {
    return page[IS_ROOT_OFFSET].toInt() != 0
}

fun getNodeKeyValue(page: ByteArray, cellNum: Int): Int {
    val offset = LEAF_NODE_HEADER_SIZE + cellNum * LEAF_NODE_CELL_SIZE
    return ByteBuffer.wrap(page).getInt(offset)
}

fun getLeafNodeNumCells(page: ByteArray): Int {
    return ByteBuffer.wrap(page).getInt(LEAF_NODE_NUM_CELLS_OFFSET)
}

fun leafNodeCell(cellInNode: Int): Int {
    return LEAF_NODE_HEADER_SIZE + cellInNode * LEAF_NODE_CELL_SIZE
}

fun getInternalNodeNumKeys(page: ByteArray): Int {
    return ByteBuffer.wrap(page).getInt(INTERNAL_NODE_NUM_KEYS_OFFSET)
}

fun setInternalNodeNumKeys(page: ByteArray, numKeys: Int) {
    ByteBuffer.wrap(page).putInt(INTERNAL_NODE_NUM_KEYS_OFFSET, numKeys)
}

fun getRightChild(page: ByteArray): Int {
    return ByteBuffer.wrap(page).getInt(INTERNAL_NODE_RIGHT_CHILD_OFFSET)
}

fun setRightChild(page: ByteArray, pageNum: Int) {
    ByteBuffer.wrap(page).putInt(INTERNAL_NODE_RIGHT_CHILD_OFFSET, pageNum)
}

fun getInternalNodeChild(page: ByteArray, childNum: Int): Int {
    val numKeys = getInternalNodeNumKeys(page)

    return if (childNum == numKeys) {
        getRightChild(page)
    } else {
        val offset = INTERNAL_NODE_HEADER_SIZE + childNum * INTERNAL_NODE_CELL_SIZE
        ByteBuffer.wrap(page).getInt(offset)
    }
}

fun setInternalNodeChild(page: ByteArray, index: Int, childPageNum: Int) {
    if (childPageNum == 0) {
        println("WRITE ZERO CHILD! index=$index")
        Exception().printStackTrace()
    }
    val offset = INTERNAL_NODE_HEADER_SIZE + index * INTERNAL_NODE_CELL_SIZE
    ByteBuffer.wrap(page).putInt(offset, childPageNum)
}

fun setInternalNodeKey(page: ByteArray, index: Int, key: Int) {
    val offset = INTERNAL_NODE_HEADER_SIZE + index * INTERNAL_NODE_CELL_SIZE + INTERNAL_NODE_CHILD_SIZE
    ByteBuffer.wrap(page).putInt(offset, key)
}

fun getInternalNodeKey(page: ByteArray, index: Int): Int {
    val offset = INTERNAL_NODE_HEADER_SIZE + index * INTERNAL_NODE_CELL_SIZE + INTERNAL_NODE_CHILD_SIZE
    return ByteBuffer.wrap(page).getInt(offset)
}

fun setLeftChild(page: ByteArray, childPageNum: Int) {
    setInternalNodeChild(page, 0, childPageNum)
}

fun setParent(page: ByteArray, parentPageNum: Int) {
    ByteBuffer.wrap(page).putInt(PARENT_POINTER_OFFSET, parentPageNum)
}

fun getParent(page: ByteArray): Int {
    return ByteBuffer.wrap(page).getInt(PARENT_POINTER_OFFSET)
}

fun getMaxNodeKey(pager: Pager, page: ByteArray): Int {
    return when (getNodeType(page)) {
        NodeType.NODE_LEAF -> getNodeKeyValue(page, getLeafNodeNumCells(page) - 1)
        NodeType.NODE_INTERNAL -> getMaxNodeKey(pager, pager.getPage(getRightChild(page)))
    }
}

fun printLeaf(page: ByteArray) {
    val cellNum = getLeafNodeNumCells(page)
    print("[ ")
    for (i in 0 until cellNum) {
        print("${getNodeKeyValue(page, i)} ")
    }
    println("]")
}

fun getLeafNextLeafNode(page: ByteArray): Int {
    return ByteBuffer.wrap(page).getInt(LEAF_NODE_NEXT_LEAF_OFFSET)
}

fun setLeafNextLeafNode(page: ByteArray, nextLeafNode: Int) {
    ByteBuffer.wrap(page).putInt(LEAF_NODE_NEXT_LEAF_OFFSET, nextLeafNode)
}


fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("Must supply a database filename.")
        exitProcess(1)
    }
    print(LEAF_NODE_MAX_CELLS)
    val table = dbOpen(args[0])
    while (true) {
        printUsage()
        val command = readlnOrNull() ?: "Undefined"
        processCommand(command, table)

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

fun processCommand(command: String, table: Table) {
    if (command.startsWith(".")) {
        when (doMetaCommand(command, table)) {
            MetaCommands.META_COMMAND_SUCCESS -> return
            MetaCommands.META_COMMAND_UNDEFINED -> print("Undefined command $command")
        }
    } else {
        val statement = prepareStatement(command, table)
        when (statement.statementStatus) {
            StatementStatus.PREPARE_SUCCESS -> executeStatement(statement)
            StatementStatus.PREPARE_UNDEFINED -> print("Undefined command $command")
        }

    }
}

fun executeStatement(statement: PreparedStatement) {
    when (statement.statementType) {
        StatementType.STATEMENT_INSERT -> executeInsert(statement)
        StatementType.STATEMENT_SELECT -> executeSelect(statement)
        StatementType.STATEMENT_UPDATE -> TODO()
        StatementType.STATEMENT_DELETE -> TODO()
        StatementType.STATEMENT_UNDEFINED -> TODO()
    }
}

fun executeSelect(statement: PreparedStatement) {
    val cursor = tableStart(statement.table)
    while (!cursor.endOfTable) {
        println(deserializeRow(cursor))
        cursor.cursorAdvance()
    }
}

fun deserializeRow(cursor: Cursor): Row {
    val username = ByteArray(USERNAME_SIZE)
    val email = ByteArray(EMAIL_SIZE)

    val pageNum = cursor.pageNum
    val cellOffset = LEAF_NODE_HEADER_SIZE + cursor.cellNum * LEAF_NODE_CELL_SIZE + LEAF_NODE_KEY_SIZE
    val page = cursor.table.pager.getPage(pageNum)

    val id = ByteBuffer.wrap(page)
        .getInt(cellOffset + ID_OFFSET)
    System.arraycopy(page, cellOffset + USERNAME_OFFSET, username, 0, USERNAME_SIZE)
    System.arraycopy(page, cellOffset + EMAIL_OFFSET, email, 0, EMAIL_SIZE)
    return Row(
        Id = id,
        Username = String(username).trimEnd('\u0000'),
        Email = String(email).trimEnd('\u0000')
    )
}


fun executeInsert(statement: PreparedStatement) {
    val parameters = statement.statement.split(" ").filter { it.isNotBlank() && it != "insert" }
    println(parameters.joinToString(" "))
    val row = Row(
        Id = parameters[0].toInt(),
        Username = parameters[1],
        Email = parameters[2],
    )
    val cursor = tableFind(
        statement.table,
        key = row.Id
    )

    val node = cursor.table.pager.getPage(cursor.pageNum)
    val numCells = getLeafNodeNumCells(node)
    if (cursor.cellNum < numCells) {
        if (getNodeKeyValue(node, cursor.cellNum) == row.Id) {
            println("Duplicate key")
            return
        }
    }
    if (numCells >= LEAF_NODE_MAX_CELLS) {
        leafNodeSplitAndInsert(cursor, row)
    } else {
        leafNodeInsert(cursor, row)
    }
}

fun leafNodeSplitAndInsert(cursor: Cursor, row: Row) {
    val oldNode = cursor.table.pager.getPage(cursor.pageNum)
    val oldMax = getMaxNodeKey(cursor.table.pager, oldNode)
    val newPageNum = cursor.table.pager.numPages
    val newNode = cursor.table.pager.getPage(newPageNum)
    initializeLeafNode(newNode)
    setParent(newNode, getParent(oldNode))
    setLeafNextLeafNode(newNode, getLeafNextLeafNode(oldNode))
    setLeafNextLeafNode(oldNode, newPageNum)
    for (i in LEAF_NODE_MAX_CELLS downTo 0) {
        val destinationNode = if (i >= LEAF_NODE_LEFT_SPLIT_COUNT) {
            newNode
        } else {
            oldNode
        }
        val cellInNode = if (destinationNode === oldNode) i else i - LEAF_NODE_LEFT_SPLIT_COUNT
        val destination = leafNodeCell(cellInNode)
        when {
            i == cursor.cellNum -> {
                serialize(row = row, page = destinationNode, cellInNode)
            }

            i >= cursor.cellNum -> {
                System.arraycopy(
                    oldNode,
                    leafNodeCell(i - 1),
                    destinationNode,
                    destination,
                    LEAF_NODE_CELL_SIZE
                )
            }

            else -> {
                System.arraycopy(
                    oldNode,
                    leafNodeCell(i),
                    destinationNode,
                    destination,
                    LEAF_NODE_CELL_SIZE
                )
            }
        }
    }
    setLeafNodeNumCells(oldNode, LEAF_NODE_LEFT_SPLIT_COUNT)
    setLeafNodeNumCells(newNode, LEAF_NODE_RIGHT_SPLIT_COUNT)
    if (getIsRoot(oldNode)) {
        return createNewRoot(cursor.table, newPageNum)
    } else {
        val parentPageNum = getParent(oldNode)
        val newMax = getMaxNodeKey(cursor.table.pager, oldNode)
        val parent = cursor.table.pager.getPage(parentPageNum)
        updateInternalNodeKey(parent, oldMax, newMax)
        internalNodeInsert(cursor.table, parentPageNum, newPageNum)
        return
    }
}

fun internalNodeInsert(table: Table, parentPageNum: Int, childPageNum: Int) {
    val parent = table.pager.getPage(parentPageNum)
    val child = table.pager.getPage(childPageNum)
    val childMaxKey = getMaxNodeKey(table.pager, child)
    val index = internalNodeFindChild(parent, childMaxKey)
    val numKeys = getInternalNodeNumKeys(parent)

    if (numKeys >= INTERNAL_NODE_MAX_CELLS) {
        internalNodeSplitAndInsert(table, parentPageNum, childPageNum)
        return
    }

    val rightChildPageNum = getRightChild(parent)
    if (rightChildPageNum == INVALID_PAGE_NUM) {
        setRightChild(parent, childPageNum)
        return
    }

    setInternalNodeNumKeys(parent, numKeys + 1)
    val rightChild = table.pager.getPage(rightChildPageNum)
    if (childMaxKey > getMaxNodeKey(table.pager, rightChild)) {
        setInternalNodeChild(parent, numKeys, rightChildPageNum)
        setInternalNodeKey(parent, numKeys, getMaxNodeKey(table.pager, rightChild))
        setRightChild(parent, childPageNum)
    } else {
        for (i in numKeys downTo index + 1) {
            System.arraycopy(
                parent,
                internalNodeCell(i - 1),
                parent,
                internalNodeCell(i),
                INTERNAL_NODE_CELL_SIZE
            )
        }
        setInternalNodeChild(parent, index, childPageNum)
        setInternalNodeKey(parent, index, childMaxKey)
    }
}

fun internalNodeSplitAndInsert(
    table: Table,
    parentPageNum: Int,
    childPageNum: Int
) {
    var oldPageNum = parentPageNum
    var oldNode = table.pager.getPage(parentPageNum)
    val oldMax = getMaxNodeKey(table.pager, oldNode)

    val child = table.pager.getPage(childPageNum)
    val childMax = getMaxNodeKey(table.pager, child)

    val newPageNum = table.pager.numPages

    val splittingRoot = getIsRoot(oldNode)

    val parent: ByteArray
    var newNode: ByteArray? = null

    if (splittingRoot) {
        createNewRoot(table, newPageNum)

        parent = table.pager.getPage(table.rootPageNum)

        oldPageNum = getInternalNodeChild(parent, 0)
        oldNode = table.pager.getPage(oldPageNum)
    } else {
        parent = table.pager.getPage(getParent(oldNode))

        newNode = table.pager.getPage(newPageNum)
        initializeInternalNode(newNode)
    }

    var oldNumKeys = getInternalNodeNumKeys(oldNode)

    var curPageNum = getRightChild(oldNode)
    var cur = table.pager.getPage(curPageNum)
    internalNodeInsert(table, newPageNum, curPageNum)
    setParent(cur, newPageNum)
    setRightChild(oldNode, INVALID_PAGE_NUM)

    for (i in INTERNAL_NODE_MAX_CELLS - 1 downTo INTERNAL_NODE_MAX_CELLS / 2 + 1) {

        curPageNum = getInternalNodeChild(oldNode, i)
        cur = table.pager.getPage(curPageNum)
        internalNodeInsert(table, newPageNum, curPageNum)
        setParent(cur, newPageNum)

        oldNumKeys--
        setInternalNodeNumKeys(oldNode, oldNumKeys)
    }
    setRightChild(
        oldNode,
        getInternalNodeChild(oldNode, oldNumKeys - 1)
    )

    oldNumKeys--
    setInternalNodeNumKeys(oldNode, oldNumKeys)

    val maxAfterSplit = getMaxNodeKey(table.pager, oldNode)

    val destinationPageNum =
        if (childMax < maxAfterSplit)
            oldPageNum
        else
            newPageNum
    internalNodeInsert(table, destinationPageNum, childPageNum)
    setParent(child, destinationPageNum)

    updateInternalNodeKey(
        parent,
        oldMax,
        getMaxNodeKey(table.pager, oldNode)
    )

    if (!splittingRoot) {
        internalNodeInsert(
            table,
            getParent(oldNode),
            newPageNum
        )
        setParent(newNode!!, getParent(oldNode))
    }
}

fun internalNodeCell(i: Int): Int {
    return INTERNAL_NODE_HEADER_SIZE + i * INTERNAL_NODE_CELL_SIZE

}

fun updateInternalNodeKey(parent: ByteArray, oldMax: Int, newMax: Int) {
    val oldChildIndex = internalNodeFindChild(parent, oldMax)
    setInternalNodeKey(parent, oldChildIndex, newMax)
}

fun createNewRoot(table: Table, rightChildPageNum: Int) {
    val root = table.pager.getPage(table.rootPageNum)
    val rightChild = table.pager.getPage(rightChildPageNum)
    val leftChildPageNum = table.pager.numPages
    val leftChild = table.pager.getPage(leftChildPageNum)
    if (getNodeType(root) == NodeType.NODE_INTERNAL) {
        initializeInternalNode(rightChild)
        initializeInternalNode(leftChild)
    }
    System.arraycopy(root, 0, leftChild, 0, PAGE_SIZE)
    setIsRoot(leftChild, false)
    initializeInternalNode(root)
    setInternalNodeNumKeys(root, 1)
    setIsRoot(root, true)
    setLeftChild(root, leftChildPageNum)
    setRightChild(root, rightChildPageNum)
    val maxLeftKey = getMaxNodeKey(table.pager, leftChild)
    setInternalNodeKey(root, 0, maxLeftKey)
    setParent(leftChild, table.rootPageNum)
    if (getNodeType(leftChild) == NodeType.NODE_INTERNAL) {

        val numKeys = getInternalNodeNumKeys(leftChild)

        for (i in 0 until numKeys) {
            val childPage = getInternalNodeChild(leftChild, i)
            val child = table.pager.getPage(childPage)
            setParent(child, leftChildPageNum)
        }

        val right = table.pager.getPage(getRightChild(leftChild))
        setParent(right, leftChildPageNum)
    }
    setParent(rightChild, table.rootPageNum)
}


fun leafNodeInsert(cursor: Cursor, row: Row) {
    val page = cursor.table.pager.getPage(cursor.pageNum)
    val numCells = getLeafNodeNumCells(page)
    shiftCells(numCells, page, cursor)
    serialize(row, page, cursor.cellNum)
    setLeafNodeNumCells(page, numCells + 1)
}

fun shiftCells(numCells: Int, page: ByteArray, cursor: Cursor) {
    for (i in numCells downTo cursor.cellNum + 1) {
        val destination = LEAF_NODE_HEADER_SIZE + i * LEAF_NODE_CELL_SIZE
        val source = LEAF_NODE_HEADER_SIZE + (i - 1) * LEAF_NODE_CELL_SIZE
        System.arraycopy(
            page,
            source,
            page,
            destination,
            LEAF_NODE_CELL_SIZE
        )
    }

}

fun serialize(row: Row, page: ByteArray, cellNum: Int) {
    val cellOffset =
        LEAF_NODE_HEADER_SIZE +
                cellNum * LEAF_NODE_CELL_SIZE

    ByteBuffer.wrap(page).putInt(cellOffset, row.Id)
    val valueOffset = cellOffset + LEAF_NODE_KEY_SIZE
    val username = row.Username.toByteArray()
    val email = row.Email.toByteArray()
    ByteBuffer.wrap(page)
        .putInt(valueOffset + ID_OFFSET, row.Id)
    System.arraycopy(
        username,
        0,
        page,
        valueOffset + USERNAME_OFFSET,
        username.size,
    )
    System.arraycopy(
        email,
        0,
        page,
        valueOffset + EMAIL_OFFSET,
        email.size,
    )
}


fun prepareStatement(command: String, table: Table): PreparedStatement {
    if (command.startsWith("insert")) {
        return PreparedStatement(
            command,
            statementStatus = StatementStatus.PREPARE_SUCCESS,
            statementType = StatementType.STATEMENT_INSERT,
            table = table
        )
    } else if (command.startsWith("delete")) {
        return PreparedStatement(
            statement = command,
            statementStatus = StatementStatus.PREPARE_SUCCESS,
            statementType = StatementType.STATEMENT_DELETE,
            table = table

        )
    } else if (command.startsWith("select")) {
        return PreparedStatement(
            statement = command,
            statementStatus = StatementStatus.PREPARE_SUCCESS,
            statementType = StatementType.STATEMENT_SELECT,
            table = table
        )
    } else {
        return PreparedStatement(
            statement = command,
            statementStatus = StatementStatus.PREPARE_UNDEFINED,
            statementType = StatementType.STATEMENT_UNDEFINED,
            table = table
        )
    }
}

fun doMetaCommand(command: String, table: Table): MetaCommands {
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


fun printUsage() {
    println()
    print("db > ")
}

fun flush(table: Table) {
    for (i in 0 until table.pager.numPages) {
        table.pager.flush(i)
    }
}

enum class MetaCommands { META_COMMAND_SUCCESS, META_COMMAND_UNDEFINED }

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


data class Row(
    val Id: Int,
    val Username: String,
    val Email: String,

    ) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Row

        if (Id != other.Id) return false
        if (!Username.contentEquals(other.Username)) return false
        if (!Email.contentEquals(other.Email)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = Id
        result = 31 * result + Username.hashCode()
        result = 31 * result + Email.hashCode()
        return result
    }

    override fun toString(): String {
        return "id: $Id, Username: $Username, Email: $Email"
    }
}

data class Pager(
    val file: RandomAccessFile,
    val pages: Array<ByteArray?> = arrayOfNulls(TABLE_MAX_PAGES)
) {
    var numPages: Int =
        ((file.length() - HEADER_SIZE).coerceAtLeast(0) / PAGE_SIZE).toInt()

    fun getPage(pageNum: Int): ByteArray {
        if (pageNum !in 0..TABLE_MAX_PAGES) {
            exitProcess(-1)
        }
        pages[pageNum]?.let { return it }

        val page = loadPageFromFile(pageNum)
        pages[pageNum] = page
        if (pageNum >= numPages) {
            numPages = pageNum + 1
        }

        return page
    }

    private fun loadPageFromFile(pageNum: Int): ByteArray {
        val numPages = (file.length() + PAGE_SIZE - 1) / PAGE_SIZE
        if (pageNum >= numPages) {
            return ByteArray(PAGE_SIZE)
        }
        val page = ByteArray(PAGE_SIZE)

        file.seek(HEADER_SIZE + pageNum * PAGE_SIZE.toLong())
        file.read(page)
        return page
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Pager

        if (file != other.file) return false
        if (!pages.contentDeepEquals(other.pages)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = file.hashCode()
        result = 31 * result + pages.contentDeepHashCode()
        return result
    }

    fun flush(i: Int) {
        val page = pages[i] ?: return
        file.seek(HEADER_SIZE + i * PAGE_SIZE.toLong())
        file.write(page)
    }

}

data class Table(
    val pager: Pager,
    val rootPageNum: Int
)

data class Cursor(
    val table: Table,
    var pageNum: Int,
    var cellNum: Int,
    var endOfTable: Boolean,
) {
    fun cursorAdvance() {

        val page = this.table.pager.getPage(this.pageNum)
        this.cellNum++
        if (this.cellNum >= getLeafNodeNumCells(page)) {
            val nextPage = getLeafNextLeafNode(page)
            if (nextPage == 0) {
                this.endOfTable = true
            } else {
                this.pageNum = nextPage
                this.cellNum = 0
            }
        }
    }
}

fun tableStart(table: Table): Cursor {
    val cursor = tableFind(table, 0)
    val node = table.pager.getPage(cursor.pageNum)
    val numCells = getLeafNodeNumCells(node)
    cursor.endOfTable = numCells == 0
    return cursor
}

fun tableEnd(table: Table): Cursor {
    val page = table.pager.getPage(0)

    return Cursor(
        table = table,
        pageNum = 0,
        cellNum = getLeafNodeNumCells(page),
        endOfTable = true
    )
}

fun tableFind(table: Table, key: Int): Cursor {
    val node = table.pager.getPage(table.rootPageNum)
    return when (getNodeType(node)) {
        NodeType.NODE_LEAF -> {
            leafNodeFind(table, table.rootPageNum, node, key)
        }

        NodeType.NODE_INTERNAL -> {
            internalNodeFind(table, table.rootPageNum, key)
        }
    }
}

fun internalNodeFindChild(node: ByteArray, key: Int): Int {
    val numKeys = getInternalNodeNumKeys(node)
    var minIndex = 0
    var maxIndex = numKeys
    while (minIndex != maxIndex) {
        val index = (minIndex + maxIndex) / 2
        val keyToRight = getInternalNodeKey(node, index)
        if (keyToRight >= key) {
            maxIndex = index
        } else {
            minIndex = index + 1
        }
    }
    return minIndex

}

fun internalNodeFind(table: Table, pageNum: Int, key: Int): Cursor {
    val node = table.pager.getPage(pageNum)
    val childIndex = internalNodeFindChild(node, key)
    val child = getInternalNodeChild(node, childIndex)
    val childNode = table.pager.getPage(child)
    return when (getNodeType(childNode)) {
        NodeType.NODE_LEAF -> leafNodeFind(table, child, childNode, key)
        NodeType.NODE_INTERNAL -> internalNodeFind(table, child, key)
    }
}

fun leafNodeFind(table: Table, pageNum: Int, node: ByteArray, key: Int): Cursor {
    val numCells = getLeafNodeNumCells(node)
    val cursor = Cursor(
        table = table,
        pageNum = pageNum,
        cellNum = numCells,
        endOfTable = false,
    )

    var minIndex = 0
    var onePastMinIndex = numCells
    while (onePastMinIndex != minIndex) {
        val index = (minIndex + onePastMinIndex) / 2
        val indexValue = getNodeKeyValue(node, index)
        if (indexValue == key) {
            cursor.cellNum = index
            return cursor
        }
        if (key < indexValue) {
            onePastMinIndex = index
        } else {
            minIndex = index + 1
        }
    }
    cursor.cellNum = minIndex
    return cursor
}


enum class NodeType {
    NODE_INTERNAL, NODE_LEAF
}