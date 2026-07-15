package com.zxcjabka.game

import java.nio.ByteBuffer


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
const val INTERNAL_NODE_MIN_KEYS = INTERNAL_NODE_MAX_CELLS / 2

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


fun getLeafNextLeafNode(page: ByteArray): Int {
    return ByteBuffer.wrap(page).getInt(LEAF_NODE_NEXT_LEAF_OFFSET)
}

fun setLeafNextLeafNode(page: ByteArray, nextLeafNode: Int) {
    ByteBuffer.wrap(page).putInt(LEAF_NODE_NEXT_LEAF_OFFSET, nextLeafNode)
}

fun internalNodeCell(i: Int): Int {
    return INTERNAL_NODE_HEADER_SIZE + i * INTERNAL_NODE_CELL_SIZE
}

fun updateInternalNodeKey(parent: ByteArray, oldMax: Int, newMax: Int) {
    val oldChildIndex = internalNodeFindChild(parent, oldMax)
    setInternalNodeKey(parent, oldChildIndex, newMax)
}

fun updateNodeMaxInParent(pager: Pager, childPageNum: Int, newMax: Int) {
    val child = pager.getPage(childPageNum)
    if (getIsRoot(child)) return
    val parentPageNum = getParent(child)
    val parent = pager.getPage(parentPageNum)
    val idx = findChildIndex(parent, childPageNum) ?: return
    if (idx == getInternalNodeNumKeys(parent)) {
        updateNodeMaxInParent(pager, parentPageNum, newMax)
    } else {
        setInternalNodeKey(parent, idx, newMax)
    }
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

fun collapseRoot(table: Table, root: ByteArray) {
    val onlyChildPageNum = getRightChild(root)
    val onlyChild = table.pager.getPage(onlyChildPageNum)
    System.arraycopy(onlyChild, 0, root, 0, PAGE_SIZE)
    setIsRoot(root, true)
    setParent(root, 0)
    if (getNodeType(root) == NodeType.NODE_INTERNAL) {
        val numKeys = getInternalNodeNumKeys(root)
        for (i in 0..numKeys) {
            setParent(table.pager.getPage(getInternalNodeChild(root, i)), table.rootPageNum)
        }
    }
}

fun findChildIndex(parent: ByteArray, page: Int): Int? {
    val keys = getInternalNodeNumKeys(parent)
    for( i in 0..keys){
        if(getInternalNodeChild(parent, i) == page){
            return i
        }
    }
    return null
}

enum class NodeType {
    NODE_INTERNAL, NODE_LEAF
}