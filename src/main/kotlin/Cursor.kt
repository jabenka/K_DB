package com.zxcjabka.game


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

    fun existsById(
        id: Int,
    ): Boolean {
        val page = this.table.pager.getPage(this.pageNum)
        return !((this.cellNum >= getLeafNodeNumCells(page)) || (getNodeKeyValue(
            page,
            this.cellNum
        ) != id))
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