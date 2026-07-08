import com.zxcjabka.game.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import java.nio.ByteBuffer
import java.nio.file.Path
import kotlin.test.Test

class MainTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var table: Table

    @BeforeEach
    fun setUp() {
        val db = tempDir.resolve("test.db").toString()
        table = dbOpen(db)
    }

    @Test
    fun `insert and select`() {
        processCommand("insert 1 fofo baba", table)
        processCommand("insert 2 foo bar", table)

        val cursor = tableStart(table)

        assertFalse(cursor.endOfTable)

        assertEquals(
            Row(1, "fofo", "baba"),
            deserializeRow(cursor)
        )

        cursor.cursorAdvance()

        assertEquals(
            Row(2, "foo", "bar"),
            deserializeRow(cursor)
        )

        cursor.cursorAdvance()

        assertTrue(cursor.endOfTable)
    }

    @Test
    fun `leaf node count increases after insert`() {
        processCommand("insert 1 foo bar", table)
        processCommand("insert 2 baz qaz", table)

        val page = table.pager.getPage(0)

        assertEquals(2, getLeafNodeNumCells(page))
    }

    @Test
    fun `table start on empty table`() {
        val cursor = tableStart(table)

        assertTrue(cursor.endOfTable)
        assertEquals(0, cursor.cellNum)
        assertEquals(0, cursor.pageNum)
    }

    @Test
    fun `table end points after last cell`() {
        processCommand("insert 1 foo bar", table)
        processCommand("insert 2 baz qaz", table)

        val cursor = tableEnd(table)

        assertEquals(2, cursor.cellNum)
        assertEquals(0, cursor.pageNum)
        assertTrue(cursor.endOfTable)
    }

    @Test
    fun `cursor advances correctly`() {
        processCommand("insert 1 foo bar", table)
        processCommand("insert 2 baz qaz", table)

        val cursor = tableStart(table)

        assertEquals(0, cursor.cellNum)

        cursor.cursorAdvance()
        assertEquals(1, cursor.cellNum)
        assertFalse(cursor.endOfTable)

        cursor.cursorAdvance()
        assertTrue(cursor.endOfTable)
    }

    @Test
    fun `keys are written correctly`() {
        processCommand("insert 10 foo bar", table)
        processCommand("insert 20 baz qaz", table)

        val page = table.pager.getPage(0)

        val key0 = ByteBuffer.wrap(page)
            .getInt(LEAF_NODE_HEADER_SIZE)

        val key1 = ByteBuffer.wrap(page)
            .getInt(LEAF_NODE_HEADER_SIZE + LEAF_NODE_CELL_SIZE)

        assertEquals(10, key0)
        assertEquals(20, key1)
    }

    @Test
    fun `fill leaf node`() {
        repeat(LEAF_NODE_MAX_CELLS) {
            processCommand(
                "insert ${it + 1} user${it + 1} email${it + 1}",
                table
            )
        }

        val page = table.pager.getPage(0)

        assertEquals(
            LEAF_NODE_MAX_CELLS,
            getLeafNodeNumCells(page)
        )
    }

    @Test
    fun `leaf split creates correct tree`() {
        repeat(LEAF_NODE_MAX_CELLS) {
            processCommand(
                "insert ${it + 1} u${it + 1} e${it + 1}",
                table
            )
        }

        processCommand(
            "insert ${LEAF_NODE_MAX_CELLS + 1} u e",
            table
        )

        val root = table.pager.getPage(table.rootPageNum)

        assertEquals(NodeType.NODE_INTERNAL, getNodeType(root))

        assertEquals(1, getInternalNodeNumKeys(root))

        val leftPage = getInternalNodeChild(root, 0)
        val rightPage = getRightChild(root)

        val leftNode = table.pager.getPage(leftPage)
        val rightNode = table.pager.getPage(rightPage)

        assertEquals(NodeType.NODE_LEAF, getNodeType(leftNode))
        assertEquals(NodeType.NODE_LEAF, getNodeType(rightNode))

        assertEquals(
            LEAF_NODE_LEFT_SPLIT_COUNT,
            getLeafNodeNumCells(leftNode)
        )

        assertEquals(
            LEAF_NODE_RIGHT_SPLIT_COUNT,
            getLeafNodeNumCells(rightNode)
        )

        assertEquals(
            getNodeKeyValue(leftNode, LEAF_NODE_LEFT_SPLIT_COUNT - 1),
            getInternalNodeKey(root, 0)
        )
    }

    @Test
    fun `all keys survive split`() {
        repeat(LEAF_NODE_MAX_CELLS + 1) {
            processCommand(
                "insert ${it + 1} u${it + 1} e${it + 1}",
                table
            )
        }

        val root = table.pager.getPage(0)

        val left =
            table.pager.getPage(getInternalNodeChild(root, 0))

        val right =
            table.pager.getPage(getRightChild(root))

        val keys = mutableListOf<Int>()

        repeat(getLeafNodeNumCells(left)) {
            keys += getNodeKeyValue(left, it)
        }

        repeat(getLeafNodeNumCells(right)) {
            keys += getNodeKeyValue(right, it)
        }

        assertEquals(
            (1..LEAF_NODE_MAX_CELLS + 1).toList(),
            keys
        )
    }

    @Test
    fun `split root creates internal root`() {
        repeat(LEAF_NODE_MAX_CELLS + 1) {
            processCommand(
                "insert ${it + 1} u${it + 1} e${it + 1}",
                table
            )
        }

        val root = table.pager.getPage(0)

        assertEquals(NodeType.NODE_INTERNAL, getNodeType(root))
        assertTrue(getIsRoot(root))
        assertEquals(1, getInternalNodeNumKeys(root))
    }

    @Test
    fun `old root copied into left child`() {
        repeat(LEAF_NODE_MAX_CELLS + 1) {
            processCommand(
                "insert ${it + 1} u${it + 1} e${it + 1}",
                table
            )
        }

        val root = table.pager.getPage(0)

        val leftPage = getInternalNodeChild(root, 0)
        val left = table.pager.getPage(leftPage)

        assertEquals(NodeType.NODE_LEAF, getNodeType(left))
        assertFalse(getIsRoot(left))
        assertEquals(LEAF_NODE_LEFT_SPLIT_COUNT, getLeafNodeNumCells(left))
    }

    @Test
    fun `right leaf created correctly`() {
        repeat(LEAF_NODE_MAX_CELLS + 1) {
            processCommand(
                "insert ${it + 1} u${it + 1} e${it + 1}",
                table
            )
        }

        val root = table.pager.getPage(0)

        val rightPage = getRightChild(root)
        val right = table.pager.getPage(rightPage)

        assertEquals(NodeType.NODE_LEAF, getNodeType(right))
        assertFalse(getIsRoot(right))
        assertEquals(LEAF_NODE_RIGHT_SPLIT_COUNT, getLeafNodeNumCells(right))
    }

    @Test
    fun `find after root split`() {
        repeat(LEAF_NODE_MAX_CELLS + 1) {
            processCommand(
                "insert ${it + 1} u${it + 1} e${it + 1}",
                table
            )
        }

        val cursor1 = tableFind(table, 3)
        assertEquals(2, cursor1.pageNum)

        val cursor2 = tableFind(table, 12)
        assertEquals(1, cursor2.pageNum)
    }

    @Test
    fun `find returns correct row after split`() {
        repeat(LEAF_NODE_MAX_CELLS + 1) {
            processCommand(
                "insert ${it + 1} u${it + 1} e${it + 1}",
                table
            )
        }

        val cursor = tableFind(table, 10)

        val row = deserializeRow(cursor)

        assertEquals(10, row.Id)
    }

    @Test
    fun `internal node find chooses correct child`() {
        repeat(LEAF_NODE_MAX_CELLS + 1) {
            processCommand(
                "insert ${it + 1} u${it + 1} e${it + 1}",
                table
            )
        }

        val leftCursor = tableFind(table, 3)
        assertEquals(2, leftCursor.pageNum)
        assertEquals(2, leftCursor.cellNum)

        val borderCursor = tableFind(table, 7)
        assertEquals(2, borderCursor.pageNum)
        assertEquals(6, borderCursor.cellNum)

        val rightCursor = tableFind(table, 8)
        assertEquals(1, rightCursor.pageNum)
        assertEquals(0, rightCursor.cellNum)

        val lastCursor = tableFind(table, 14)
        assertEquals(1, lastCursor.pageNum)
        assertEquals(6, lastCursor.cellNum)
    }

    @Test
    fun `internal node find returns correct page for every key`() {
        repeat(LEAF_NODE_MAX_CELLS + 1) {
            processCommand(
                "insert ${it + 1} u${it + 1} e${it + 1}",
                table
            )
        }

        for (key in 1..14) {
            val cursor = tableFind(table, key)

            if (key <= 7) {
                assertEquals(
                    2,
                    cursor.pageNum,
                    "Key $key should be in left leaf"
                )
            } else {
                assertEquals(
                    1,
                    cursor.pageNum,
                    "Key $key should be in right leaf"
                )
            }
        }
    }
    @Test
    fun `insert into left leaf after root split keeps order`() {
        repeat(LEAF_NODE_MAX_CELLS + 1) {
            processCommand(
                "insert ${it + 10} u${it + 10} e${it + 10}",
                table
            )
        }

        processCommand("insert 5 u5 e5", table)

        val cursor = tableStart(table)

        assertEquals(5, deserializeRow(cursor).Id)

        cursor.cursorAdvance()

        assertEquals(10, deserializeRow(cursor).Id)
    }


    @Test
    fun `insert into right leaf after root split keeps order`() {
        repeat(LEAF_NODE_MAX_CELLS + 1) {
            processCommand(
                "insert ${it + 1} u${it + 1} e${it + 1}",
                table
            )
        }

        processCommand("insert 100 u100 e100", table)

        val cursor = tableFind(table, 100)

        assertEquals(100, deserializeRow(cursor).Id)
    }


    @Test
    fun `leaf linked list contains all pages after multiple inserts`() {
        repeat(LEAF_NODE_MAX_CELLS * 2 + 1) {
            processCommand(
                "insert ${it + 1} u${it + 1} e${it + 1}",
                table
            )
        }

        val root = table.pager.getPage(0)

        val first = getInternalNodeChild(root, 0)
        val second = getInternalNodeChild(root, 1)
        val third = getRightChild(root)

        assertEquals(second, getLeafNextLeafNode(table.pager.getPage(first)))
        assertEquals(third, getLeafNextLeafNode(table.pager.getPage(second)))
        assertEquals(0, getLeafNextLeafNode(table.pager.getPage(third)))
    }


    @Test
    fun `select after many inserts returns sorted rows`() {
        val ids = listOf(
            20, 5, 15, 1, 30, 10, 25, 2
        )

        ids.forEach {
            processCommand(
                "insert $it u$it e$it",
                table
            )
        }

        val cursor = tableStart(table)

        val result = mutableListOf<Int>()

        while (!cursor.endOfTable) {
            result += deserializeRow(cursor).Id
            cursor.cursorAdvance()
        }

        assertEquals(
            listOf(1,2,5,10,15,20,25,30),
            result
        )
    }


    @Test
    fun `find nonexistent key returns insertion position`() {
        repeat(LEAF_NODE_MAX_CELLS + 1) {
            processCommand(
                "insert ${it + 1} u${it + 1} e${it + 1}",
                table
            )
        }

        val cursor = tableFind(table, 50)

        assertEquals(
            LEAF_NODE_MAX_CELLS / 2 + 1,
            cursor.cellNum
        )
    }


    @Test
    fun `duplicate key is not inserted`() {
        processCommand(
            "insert 1 user email",
            table
        )

        processCommand(
            "insert 1 user2 email2",
            table
        )

        val cursor = tableStart(table)

        assertEquals(
            Row(1, "user", "email"),
            deserializeRow(cursor)
        )

        cursor.cursorAdvance()

        assertTrue(cursor.endOfTable)
    }


    @Test
    fun `parent pointer set after root split`() {
        repeat(LEAF_NODE_MAX_CELLS + 1) {
            processCommand(
                "insert ${it + 1} u${it + 1} e${it + 1}",
                table
            )
        }

        val root = table.pager.getPage(0)

        val left = getInternalNodeChild(root,0)
        val right = getRightChild(root)

        assertEquals(
            0,
            getParent(table.pager.getPage(left))
        )

        assertEquals(
            0,
            getParent(table.pager.getPage(right))
        )
    }


    @Test
    fun `leaf split keeps all original rows`() {
        repeat(LEAF_NODE_MAX_CELLS + 1) {
            processCommand(
                "insert ${it + 1} u${it + 1} e${it + 1}",
                table
            )
        }

        val cursor = tableStart(table)

        val rows = mutableListOf<Row>()

        while (!cursor.endOfTable) {
            rows += deserializeRow(cursor)
            cursor.cursorAdvance()
        }

        assertEquals(
            (1..LEAF_NODE_MAX_CELLS + 1)
                .map { Row(it, "u$it", "e$it") },
            rows
        )
    }


    @Test
    fun `insert after split does not corrupt existing pages`() {
        repeat(LEAF_NODE_MAX_CELLS + 1) {
            processCommand(
                "insert ${it + 1} u${it + 1} e${it + 1}",
                table
            )
        }

        processCommand(
            "insert 100 u100 e100",
            table
        )

        val cursor = tableStart(table)

        val ids = mutableListOf<Int>()

        while (!cursor.endOfTable) {
            ids += deserializeRow(cursor).Id
            cursor.cursorAdvance()
        }

        assertEquals(
            (1..LEAF_NODE_MAX_CELLS + 1).toList() + 100,
            ids
        )
    }


    @Test
    fun `all leaves have correct node type after two splits`() {
        repeat(LEAF_NODE_MAX_CELLS * 2 + 1) {
            processCommand(
                "insert ${it + 1} u${it + 1} e${it + 1}",
                table
            )
        }

        val root = table.pager.getPage(0)

        repeat(2) {
            assertEquals(
                NodeType.NODE_LEAF,
                getNodeType(
                    table.pager.getPage(
                        getInternalNodeChild(root,it)
                    )
                )
            )
        }

        assertEquals(
            NodeType.NODE_LEAF,
            getNodeType(
                table.pager.getPage(getRightChild(root))
            )
        )
    }


    @Test
    fun `root remains internal after second split`() {
        repeat(LEAF_NODE_MAX_CELLS * 2 + 1) {
            processCommand(
                "insert ${it + 1} u${it + 1} e${it + 1}",
                table
            )
        }

        val root = table.pager.getPage(0)

        assertEquals(
            NodeType.NODE_INTERNAL,
            getNodeType(root)
        )

        assertTrue(getIsRoot(root))
    }

    @Test
    fun `dump tree`(){
        repeat(LEAF_NODE_MAX_CELLS * 3 + 1) {
            processCommand(
                "insert ${it + 1} u${it + 1} e${it + 1}",
                table
            )
        }

        dumpTree(table, 0)
    }
}