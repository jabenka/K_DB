package com.zxcjabka.game

import java.nio.ByteBuffer


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