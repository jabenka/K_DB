import com.zxcjabka.game.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test

class IntegrationalTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var table: Table
    private lateinit var cp: CommandProcessor
    private lateinit var se: StatementExecutor

    @BeforeEach
    fun setUp() {
        val db = tempDir.resolve("test.db").toString()
        table = dbOpen(db)
        se = StatementExecutor()
        cp = CommandProcessor(
            statementExecutor = se
        )
    }

    @Test
    fun `all inserted rows can be found after split`() {
        repeat(LEAF_NODE_MAX_CELLS + 1) {
            cp.processCommand(
                "insert ${it + 1} u${it + 1} e${it + 1}",
                table
            )
        }

        for (i in 1..LEAF_NODE_MAX_CELLS + 1) {
            val cursor = tableFind(table, i)
            val row = deserializeRow(cursor)

            assertEquals(i, row.Id)
        }
    }

    @Test
    fun `insert after split`() {
        repeat(LEAF_NODE_MAX_CELLS + 1) {
            cp.processCommand(
                "insert ${it + 1} u${it + 1} e${it + 1}",
                table
            )
        }

        cp.processCommand(
            "insert ${LEAF_NODE_MAX_CELLS + 2} ux ex",
            table
        )

        val cursor = tableFind(table, LEAF_NODE_MAX_CELLS + 2)
        val row = deserializeRow(cursor)

        assertEquals(LEAF_NODE_MAX_CELLS + 2, row.Id)
    }

    @Test
    fun `insert in middle after split`() {
        repeat(LEAF_NODE_MAX_CELLS + 1) {
            cp.processCommand(
                "insert ${(it + 1) * 2} u e",
                table
            )
        }

        cp.processCommand("insert 5 five five", table)

        val row = deserializeRow(tableFind(table, 5))

        assertEquals(5, row.Id)
    }

    @Test
    fun `insert 100 sequential keys`() {
        repeat(100) {
            cp.processCommand("insert ${it + 1} u${it + 1} e${it + 1}", table)
        }

        repeat(100) {
            val cursor = tableFind(table, it + 1)
            val row = deserializeRow(cursor)
            assertEquals(it + 1, row.Id)
        }
    }

    @Test
    fun `select after root split starts from smallest key`() {
        repeat(LEAF_NODE_MAX_CELLS + 1) {
            cp.processCommand(
                "insert ${it + 1} u${it + 1} e${it + 1}",
                table
            )
        }

        val cursor = tableStart(table)
        val row = deserializeRow(cursor)

        assertEquals(1, row.Id)
        assertEquals("u1", row.Username)
        assertEquals("e1", row.Email)
    }

    @Test
    fun `select returns all inserted rows after root split`() {
        repeat(LEAF_NODE_MAX_CELLS + 1) {
            cp.processCommand(
                "insert ${it + 1} u${it + 1} e${it + 1}",
                table
            )
        }

        val cursor = tableStart(table)

        for (id in 1..LEAF_NODE_MAX_CELLS + 1) {
            assertFalse(
                cursor.endOfTable,
                "Cursor ended before row $id"
            )

            val row = deserializeRow(cursor)

            assertEquals(
                Row(id, "u$id", "e$id"),
                row
            )

            cursor.cursorAdvance()
        }

        assertTrue(cursor.endOfTable)
    }

    @Test
    fun `select returns 100 rows in order`() {
        repeat(100) {
            cp.processCommand(
                "insert ${it + 1} u${it + 1} e${it + 1}",
                table
            )
        }

        val cursor = tableStart(table)

        for (id in 1..100) {
            assertFalse(cursor.endOfTable)

            assertEquals(
                Row(id, "u$id", "e$id"),
                deserializeRow(cursor)
            )

            cursor.cursorAdvance()
        }

        assertTrue(cursor.endOfTable)
    }

    @Test
    fun `parent is updated after second leaf split`() {

        repeat(LEAF_NODE_MAX_CELLS * 2 + 1) {
            cp.processCommand(
                "insert ${it + 1} u${it + 1} e${it + 1}",
                table
            )
        }

        val root = table.pager.getPage(table.rootPageNum)

        assertEquals(NodeType.NODE_INTERNAL, getNodeType(root))
        assertTrue(getIsRoot(root))

        assertEquals(2, getInternalNodeNumKeys(root))

        val left = table.pager.getPage(getInternalNodeChild(root, 0))
        val middle = table.pager.getPage(getInternalNodeChild(root, 1))
        val right = table.pager.getPage(getRightChild(root))

        assertEquals(NodeType.NODE_LEAF, getNodeType(left))
        assertEquals(NodeType.NODE_LEAF, getNodeType(middle))
        assertEquals(NodeType.NODE_LEAF, getNodeType(right))

        assertEquals(7, getInternalNodeKey(root, 0))
        assertEquals(14, getInternalNodeKey(root, 1))
    }

    @Test
    fun `three leaf nodes are linked after second split`() {

        repeat(LEAF_NODE_MAX_CELLS * 2 + 1) {
            cp.processCommand(
                "insert ${it + 1} u${it + 1} e${it + 1}",
                table
            )
        }

        val root = table.pager.getPage(0)

        val left = getInternalNodeChild(root, 0)
        val middle = getInternalNodeChild(root, 1)
        val right = getRightChild(root)

        assertEquals(middle, getLeafNextLeafNode(table.pager.getPage(left)))
        assertEquals(right, getLeafNextLeafNode(table.pager.getPage(middle)))
        assertEquals(0, getLeafNextLeafNode(table.pager.getPage(right)))
    }

    @Test
    fun `select returns all rows after second leaf split`() {

        repeat(LEAF_NODE_MAX_CELLS * 2 + 1) {
            cp.processCommand(
                "insert ${it + 1} u${it + 1} e${it + 1}",
                table
            )
        }

        val cursor = tableStart(table)

        for (id in 1..(LEAF_NODE_MAX_CELLS * 2 + 1)) {
            assertFalse(cursor.endOfTable)

            assertEquals(
                Row(id, "u$id", "e$id"),
                deserializeRow(cursor)
            )

            cursor.cursorAdvance()
        }

        assertTrue(cursor.endOfTable)
    }

    @Test
    fun `internal node split creates new root`() {
        repeat(LEAF_NODE_MAX_CELLS * 4 + 1) {
            cp.processCommand(
                "insert ${it + 1} u${it + 1} e${it + 1}",
                table
            )
        }

        val root = table.pager.getPage(table.rootPageNum)

        assertEquals(NodeType.NODE_INTERNAL, getNodeType(root))
        assertTrue(getIsRoot(root))

        val left = table.pager.getPage(getInternalNodeChild(root, 0))
        val right = table.pager.getPage(getRightChild(root))

        assertEquals(NodeType.NODE_INTERNAL, getNodeType(left))
        assertEquals(NodeType.NODE_INTERNAL, getNodeType(right))
    }

    @Test
    fun `select returns all rows after internal node split`() {
        val total = LEAF_NODE_MAX_CELLS * 4 + 10

        repeat(total) {
            cp.processCommand(
                "insert ${it + 1} u${it + 1} e${it + 1}",
                table
            )
        }

        val cursor = tableStart(table)

        for (id in 1..total) {
            assertFalse(cursor.endOfTable)

            assertEquals(
                Row(id, "u$id", "e$id"),
                deserializeRow(cursor)
            )

            cursor.cursorAdvance()
        }

        assertTrue(cursor.endOfTable)
    }

    @Test
    fun `find works after internal node split`() {
        val total = LEAF_NODE_MAX_CELLS * 4 + 10

        repeat(total) {
            cp.processCommand(
                "insert ${it + 1} u${it + 1} e${it + 1}",
                table
            )
        }

        listOf(
            1,
            total / 3,
            total / 2,
            total - 1,
            total
        ).forEach { key ->
            val cursor = tableFind(table, key)

            assertEquals(
                key,
                deserializeRow(cursor).Id
            )
        }
    }

    @Test
    fun `tree height becomes three after internal split`() {
        repeat(LEAF_NODE_MAX_CELLS * 4 + 1) {
            cp.processCommand(
                "insert ${it + 1} u${it + 1} e${it + 1}",
                table
            )
        }

        val root = table.pager.getPage(0)
        dumpTree(table,table.rootPageNum)
        val leftInternal =
            table.pager.getPage(getInternalNodeChild(root, 0))

        assertEquals(
            NodeType.NODE_INTERNAL,
            getNodeType(leftInternal)
        )

        val leftLeaf =
            table.pager.getPage(
                getInternalNodeChild(leftInternal, 0)
            )

        assertEquals(
            NodeType.NODE_LEAF,
            getNodeType(leftLeaf)
        )
    }
}