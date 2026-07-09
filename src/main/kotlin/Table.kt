package com.zxcjabka.game


fun flush(table: Table) {
    for (i in 0 until table.pager.numPages) {
        table.pager.flush(i)
    }
}

data class Table(
    val pager: Pager,
    val rootPageNum: Int
)