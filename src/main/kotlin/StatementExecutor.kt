package com.zxcjabka.game

class StatementExecutor {

    fun executeStatement(statement: PreparedStatement) {
        when (statement.statementType) {
            StatementType.STATEMENT_INSERT -> executeInsert(statement)
            StatementType.STATEMENT_SELECT -> executeSelect(statement)
            StatementType.STATEMENT_UPDATE -> executeUpdate(statement)
            StatementType.STATEMENT_DELETE -> executeDelete(statement)
            StatementType.STATEMENT_UNDEFINED -> TODO()
        }
    }

    private fun executeUpdate(statement: PreparedStatement) {

        val parts = statement.statement.split("set")
        val id = parts[0].split(" ").filter { it != "update" }[0].toInt()
        val values = parts[1].split(" ").filter { !it.isEmpty() && it != "values" && it != "," }.map { it.split("=") }
            .associate { it[0] to it[1] }
        val cursor = tableFind(statement.table, id)
        val page = cursor.table.pager.getPage(cursor.pageNum)
        if ((cursor.cellNum >= getLeafNodeNumCells(page)) || (getNodeKeyValue(page, cursor.cellNum) != id)) {
            println("Not found id $id")
            return
        }
        updatePage(cursor, values)
    }

    private fun updatePage(cursor: Cursor, values: Map<String, String>) {
        val page = cursor.table.pager.getPage(cursor.pageNum)
        values.entries.forEach { updateFiled(page, it,cursor.cellNum) }

    }

    fun updateFiled(page: ByteArray, it: Map.Entry<String, String>,cellNum: Int) {
        when (val fieldName = it.key) {
            "username" -> updateUsername(page,it.value,cellNum)
            "email" -> updateEmail(page,it.value,cellNum)
            else ->{
                println("Unknown field $fieldName")
                return
            }
        }

    }

    private fun updateEmail(page: ByteArray, email: String, cellNum: Int) {
        val cellOffset =
            LEAF_NODE_HEADER_SIZE +
                    cellNum * LEAF_NODE_CELL_SIZE

        val valueOffset = cellOffset + LEAF_NODE_KEY_SIZE
        System.arraycopy(
            email.toByteArray(),
            0,
            page,
            valueOffset + EMAIL_OFFSET,
            email.toByteArray().size,
        )
    }

    private fun updateUsername(page: ByteArray, username: String,cellNum: Int) {
        val cellOffset =
            LEAF_NODE_HEADER_SIZE +
                    cellNum * LEAF_NODE_CELL_SIZE

        val valueOffset = cellOffset + LEAF_NODE_KEY_SIZE
        System.arraycopy(
            username.toByteArray(),
            0,
            page,
            valueOffset + USERNAME_OFFSET,
            username.toByteArray().size,
        )
    }

    private fun executeDelete(statement: PreparedStatement) {
        val id = statement.statement.split(" ").filter { it.isNotBlank() && it != "delete" }[0].toInt()
        val cursor = tableFind(statement.table, id)
        val page = cursor.table.pager.getPage(cursor.pageNum)
        if ((cursor.cellNum >= getLeafNodeNumCells(page)) || (getNodeKeyValue(page, cursor.cellNum) != id)) {
            println("Not found id $id")
            return
        }
        leadNodeDelete(cursor)
    }

    private fun leadNodeDelete(cursor: Cursor) {

        val page = cursor.table.pager.getPage(cursor.pageNum)
        val numCells = getLeafNodeNumCells(page)
        val oldMax = getMaxNodeKey(cursor.table.pager, page)
        for (i in cursor.cellNum until numCells - 1) {
            System.arraycopy(
                page,
                leafNodeCell(i + 1),
                page,
                leafNodeCell(i),
                LEAF_NODE_CELL_SIZE
            )
        }
        setLeafNodeNumCells(page, numCells - 1)
        if (numCells - 1 > 0) {
            val currentMax = getMaxNodeKey(cursor.table.pager, page)
            if (oldMax != currentMax) {
                updateNodeMaxInParent(cursor.table.pager, cursor.pageNum, currentMax)
            }
        }
        if (!getIsRoot(page) && numCells - 1 < LEAF_NODE_LEFT_SPLIT_COUNT) {
            rebalance(cursor, page)
        }

    }

    private fun rebalance(cursor: Cursor, page: ByteArray) {
        val parent = cursor.table.pager.getPage(getParent(page))
        val childIndex = findChildIndex(parent, cursor.pageNum)!!
        val left = if (childIndex > 0) getInternalNodeChild(parent, childIndex - 1) else null
        val right =
            if (childIndex < getInternalNodeNumKeys(parent)) getInternalNodeChild(parent, childIndex + 1) else null
        val leftPage = left?.let { cursor.table.pager.getPage(it) }
        val rightPage = right?.let { cursor.table.pager.getPage(it) }
        when {
            canBorrow(leftPage, page) -> borrowFromLeft(left!!, leftPage!!, page, cursor)
            canBorrow(rightPage, page) -> borrowFromRight(rightPage!!, page, cursor)
            left != null -> mergerWithLeft(leftPage!!, page, cursor)
            else -> mergeWithRight(right!!, page, cursor)
        }
    }

    private fun mergeWithRight(rightPageNum: Int, page: ByteArray, cursor: Cursor) {
        val rightPage = cursor.table.pager.getPage(rightPageNum)
        val pageCellNum = getLeafNodeNumCells(page)
        val rightCellNum = getLeafNodeNumCells(rightPage)
        System.arraycopy(
            rightPage, leafNodeCell(0),
            page, leafNodeCell(pageCellNum),
            rightCellNum * LEAF_NODE_CELL_SIZE
        )
        setLeafNodeNumCells(page, pageCellNum + rightCellNum)
        setLeafNextLeafNode(page, getLeafNextLeafNode(rightPage))

        val parentPageNum = getParent(page)
        val parent = cursor.table.pager.getPage(parentPageNum)
        val idxRight = findChildIndex(parent, rightPageNum)!!
        if (idxRight != getInternalNodeNumKeys(parent)) {
            setInternalNodeKey(parent, idxRight - 1, getMaxNodeKey(cursor.table.pager, page))
        }
        internalNodeDelete(parentPageNum, idxRight, cursor)
    }

    private fun mergerWithLeft(leftPage: ByteArray, page: ByteArray, cursor: Cursor) {
        val currentCellNum = getLeafNodeNumCells(page)
        System.arraycopy(
            page, leafNodeCell(0),
            leftPage, leafNodeCell(getLeafNodeNumCells(leftPage)),
            currentCellNum * LEAF_NODE_CELL_SIZE
        )
        val leftCellNum = getLeafNodeNumCells(leftPage)
        setLeafNodeNumCells(leftPage, leftCellNum + currentCellNum)
        setLeafNextLeafNode(leftPage, getLeafNextLeafNode(page))

        val parentPageNum = getParent(page)
        val parent = cursor.table.pager.getPage(parentPageNum)
        val idxPage = findChildIndex(parent, cursor.pageNum)!!
        if (idxPage != getInternalNodeNumKeys(parent)) {
            setInternalNodeKey(parent, idxPage - 1, getMaxNodeKey(cursor.table.pager, leftPage))
        }
        internalNodeDelete(parentPageNum, idxPage, cursor)
    }

    private fun internalNodeDelete(nodePageNum: Int, childIndex: Int?, cursor: Cursor) {
        if (childIndex == null) return

        val pager = cursor.table.pager
        val node = pager.getPage(nodePageNum)
        val numKeys = getInternalNodeNumKeys(node)
        val oldMax = getMaxNodeKey(pager, node)

        if (childIndex == numKeys) {
            setRightChild(node, getInternalNodeChild(node, numKeys - 1))
        } else {
            for (i in childIndex until numKeys - 1) {
                System.arraycopy(
                    node, internalNodeCell(i + 1),
                    node, internalNodeCell(i),
                    INTERNAL_NODE_CELL_SIZE
                )
            }
        }
        setInternalNodeNumKeys(node, numKeys - 1)

        if (getIsRoot(node)) {
            if (getInternalNodeNumKeys(node) == 0) {
                collapseRoot(cursor.table, node)
            }
            return
        }

        val newMax = getMaxNodeKey(pager, node)
        if (oldMax != newMax) {
            updateNodeMaxInParent(pager, nodePageNum, newMax)
        }

        if (getInternalNodeNumKeys(node) < INTERNAL_NODE_MIN_KEYS) {
            rebalanceInternal(nodePageNum, cursor)
        }
    }

    private fun rebalanceInternal(nodePageNum: Int, cursor: Cursor) {
        val pager = cursor.table.pager
        val node = pager.getPage(nodePageNum)
        val parentPageNum = getParent(node)
        val parent = pager.getPage(parentPageNum)
        val idx = findChildIndex(parent, nodePageNum)!!

        val leftNum = if (idx > 0) getInternalNodeChild(parent, idx - 1) else null
        val rightNum =
            if (idx < getInternalNodeNumKeys(parent)) getInternalNodeChild(parent, idx + 1) else null
        val left = leftNum?.let { pager.getPage(it) }
        val right = rightNum?.let { pager.getPage(it) }

        when {
            canBorrowInternal(left) -> borrowInternalFromLeft(leftNum!!, nodePageNum, parentPageNum, cursor)
            canBorrowInternal(right) -> borrowInternalFromRight(rightNum!!, nodePageNum, parentPageNum, cursor)
            leftNum != null -> mergeInternalWithLeft(leftNum, nodePageNum, parentPageNum, cursor)
            else -> mergeInternalWithRight(nodePageNum, rightNum!!, parentPageNum, cursor)
        }
    }

    private fun canBorrowInternal(sibling: ByteArray?): Boolean {
        if (sibling == null) return false
        return getInternalNodeNumKeys(sibling) > INTERNAL_NODE_MIN_KEYS
    }

    private fun borrowInternalFromLeft(
        leftPageNum: Int, nodePageNum: Int, parentPageNum: Int, cursor: Cursor
    ) {
        val pager = cursor.table.pager
        val left = pager.getPage(leftPageNum)
        val node = pager.getPage(nodePageNum)
        val parent = pager.getPage(parentPageNum)

        val movedChild = getRightChild(left)
        val movedMax = getMaxNodeKey(pager, pager.getPage(movedChild))

        val leftNumKeys = getInternalNodeNumKeys(left)
        setRightChild(left, getInternalNodeChild(left, leftNumKeys - 1))
        setInternalNodeNumKeys(left, leftNumKeys - 1)

        val nodeNumKeys = getInternalNodeNumKeys(node)
        for (i in nodeNumKeys - 1 downTo 0) {
            System.arraycopy(
                node, internalNodeCell(i),
                node, internalNodeCell(i + 1),
                INTERNAL_NODE_CELL_SIZE
            )
        }
        setInternalNodeChild(node, 0, movedChild)
        setInternalNodeKey(node, 0, movedMax)
        setInternalNodeNumKeys(node, nodeNumKeys + 1)
        setParent(pager.getPage(movedChild), nodePageNum)

        val idxNode = findChildIndex(parent, nodePageNum)!!
        setInternalNodeKey(parent, idxNode - 1, getMaxNodeKey(pager, left))
    }

    private fun borrowInternalFromRight(
        rightPageNum: Int, nodePageNum: Int, parentPageNum: Int, cursor: Cursor
    ) {
        val pager = cursor.table.pager
        val right = pager.getPage(rightPageNum)
        val node = pager.getPage(nodePageNum)
        val parent = pager.getPage(parentPageNum)

        val nodeNumKeys = getInternalNodeNumKeys(node)
        val currentRight = getRightChild(node)
        setInternalNodeChild(node, nodeNumKeys, currentRight)
        setInternalNodeKey(node, nodeNumKeys, getMaxNodeKey(pager, pager.getPage(currentRight)))

        val movedChild = getInternalNodeChild(right, 0)
        setRightChild(node, movedChild)
        setInternalNodeNumKeys(node, nodeNumKeys + 1)
        setParent(pager.getPage(movedChild), nodePageNum)

        val rightNumKeys = getInternalNodeNumKeys(right)
        for (i in 0 until rightNumKeys - 1) {
            System.arraycopy(
                right, internalNodeCell(i + 1),
                right, internalNodeCell(i),
                INTERNAL_NODE_CELL_SIZE
            )
        }
        setInternalNodeNumKeys(right, rightNumKeys - 1)

        val idxNode = findChildIndex(parent, nodePageNum)!!
        setInternalNodeKey(parent, idxNode, getMaxNodeKey(pager, node))
    }

    private fun mergeInternalWithLeft(
        leftPageNum: Int, nodePageNum: Int, parentPageNum: Int, cursor: Cursor
    ) {
        val pager = cursor.table.pager
        val left = pager.getPage(leftPageNum)
        val node = pager.getPage(nodePageNum)
        val parent = pager.getPage(parentPageNum)

        var writeIdx = getInternalNodeNumKeys(left)
        val leftRight = getRightChild(left)
        setInternalNodeChild(left, writeIdx, leftRight)
        setInternalNodeKey(left, writeIdx, getMaxNodeKey(pager, pager.getPage(leftRight)))
        writeIdx++

        val nodeNumKeys = getInternalNodeNumKeys(node)
        for (i in 0 until nodeNumKeys) {
            val child = getInternalNodeChild(node, i)
            setInternalNodeChild(left, writeIdx, child)
            setInternalNodeKey(left, writeIdx, getInternalNodeKey(node, i))
            setParent(pager.getPage(child), leftPageNum)
            writeIdx++
        }
        val nodeRight = getRightChild(node)
        setRightChild(left, nodeRight)
        setParent(pager.getPage(nodeRight), leftPageNum)
        setInternalNodeNumKeys(left, writeIdx)

        val idxNode = findChildIndex(parent, nodePageNum)!!
        if (idxNode != getInternalNodeNumKeys(parent)) {
            setInternalNodeKey(parent, idxNode - 1, getMaxNodeKey(pager, left))
        }
        internalNodeDelete(parentPageNum, idxNode, cursor)
    }

    private fun mergeInternalWithRight(
        nodePageNum: Int, rightPageNum: Int, parentPageNum: Int, cursor: Cursor
    ) {
        val pager = cursor.table.pager
        val node = pager.getPage(nodePageNum)
        val right = pager.getPage(rightPageNum)
        val parent = pager.getPage(parentPageNum)

        var writeIdx = getInternalNodeNumKeys(node)
        val nodeRight = getRightChild(node)
        setInternalNodeChild(node, writeIdx, nodeRight)
        setInternalNodeKey(node, writeIdx, getMaxNodeKey(pager, pager.getPage(nodeRight)))
        writeIdx++

        val rightNumKeys = getInternalNodeNumKeys(right)
        for (i in 0 until rightNumKeys) {
            val child = getInternalNodeChild(right, i)
            setInternalNodeChild(node, writeIdx, child)
            setInternalNodeKey(node, writeIdx, getInternalNodeKey(right, i))
            setParent(pager.getPage(child), nodePageNum)
            writeIdx++
        }
        val rightRight = getRightChild(right)
        setRightChild(node, rightRight)
        setParent(pager.getPage(rightRight), nodePageNum)
        setInternalNodeNumKeys(node, writeIdx)

        val idxRight = findChildIndex(parent, rightPageNum)!!
        if (idxRight != getInternalNodeNumKeys(parent)) {
            setInternalNodeKey(parent, idxRight - 1, getMaxNodeKey(pager, node))
        }
        internalNodeDelete(parentPageNum, idxRight, cursor)
    }


    private fun borrowFromRight(
        rightPage: ByteArray,
        page: ByteArray,
        cursor: Cursor
    ) {
        val oldMax = getMaxNodeKey(cursor.table.pager, page)
        val rightCellNum = getLeafNodeNumCells(rightPage)
        val currentCellNum = getLeafNodeNumCells(page)

        val total = rightCellNum + currentCellNum
        val targetCurrent = total / 2
        val delta = targetCurrent - currentCellNum

        if (delta <= 0) return

        System.arraycopy(
            rightPage,
            leafNodeCell(0),
            page,
            leafNodeCell(currentCellNum),
            delta * LEAF_NODE_CELL_SIZE
        )

        for (i in 0 until rightCellNum - delta) {
            System.arraycopy(
                rightPage,
                leafNodeCell(i + delta),
                rightPage,
                leafNodeCell(i),
                LEAF_NODE_CELL_SIZE
            )
        }
        setLeafNodeNumCells(page, currentCellNum + delta)
        setLeafNodeNumCells(rightPage, rightCellNum - delta)
        val newMax = getMaxNodeKey(cursor.table.pager, page)
        if (oldMax != newMax) {
            updateNodeMaxInParent(cursor.table.pager, cursor.pageNum, newMax)
        }
    }

    private fun borrowFromLeft(leftPageNum: Int, leftPage: ByteArray, page: ByteArray, cursor: Cursor) {
        val leftCellNum = getLeafNodeNumCells(leftPage)
        val currentCellNum = getLeafNodeNumCells(page)
        val oldMax = getMaxNodeKey(cursor.table.pager, leftPage)
        val total = leftCellNum + currentCellNum
        val targetCurrent = total / 2
        val delta = targetCurrent - currentCellNum

        if (delta <= 0) return
        for (i in currentCellNum - 1 downTo 0) {
            System.arraycopy(
                page,
                leafNodeCell(i),
                page,
                leafNodeCell(i + delta),
                LEAF_NODE_CELL_SIZE
            )
        }
        System.arraycopy(
            leftPage,
            leafNodeCell(leftCellNum - delta),
            page,
            leafNodeCell(0),
            delta * LEAF_NODE_CELL_SIZE
        )
        setLeafNodeNumCells(page, currentCellNum + delta)
        setLeafNodeNumCells(leftPage, leftCellNum - delta)
        val newMax = getMaxNodeKey(cursor.table.pager, leftPage)
        if (oldMax != newMax) {
            updateNodeMaxInParent(cursor.table.pager, leftPageNum, newMax)
        }
    }

    private fun canBorrow(sibling: ByteArray?, current: ByteArray): Boolean {
        if (sibling == null) return false
        val siblingNumCells = getLeafNodeNumCells(sibling)
        val currentNumCells = getLeafNodeNumCells(current)
        return siblingNumCells + currentNumCells > LEAF_NODE_MAX_CELLS
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

