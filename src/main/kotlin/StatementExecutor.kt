package com.zxcjabka.game

class StatementExecutor {

    fun executeStatement(statement: PreparedStatement) {
        when (statement.statementType) {
            StatementType.STATEMENT_INSERT -> executeInsert(statement)
            StatementType.STATEMENT_SELECT -> executeSelect(statement)
            StatementType.STATEMENT_UPDATE -> TODO()
            StatementType.STATEMENT_DELETE -> TODO()
            StatementType.STATEMENT_UNDEFINED -> TODO()
        }
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


    fun executeSelect(statement: PreparedStatement) {
        val cursor = tableStart(statement.table)
        while (!cursor.endOfTable) {
            println(deserializeRow(cursor))
            cursor.cursorAdvance()
        }
    }



    fun leafNodeInsert(cursor: Cursor, row: Row) {
        val page = cursor.table.pager.getPage(cursor.pageNum)
        val numCells = getLeafNodeNumCells(page)
        shiftCells(numCells, page, cursor)
        serialize(row, page, cursor.cellNum)
        setLeafNodeNumCells(page, numCells + 1)
    }
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


}