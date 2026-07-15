package com.zxcjabka.game

import java.io.RandomAccessFile
import kotlin.system.exitProcess


data class Pager(
    val file: RandomAccessFile,
    val pages: Array<ByteArray?> = arrayOfNulls(TABLE_MAX_PAGES)
) {
    var numPages: Int =
        ((file.length() - HEADER_SIZE).coerceAtLeast(0) / PAGE_SIZE).toInt()

    fun getPage(pageNum: Int): ByteArray {
        if (pageNum !in 0 until TABLE_MAX_PAGES) {
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
