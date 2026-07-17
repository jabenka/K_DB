import com.zxcjabka.game.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.ByteBuffer
import java.nio.file.Path
import kotlin.test.Test

class MainTest {

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
    fun `insert and select`() {
        cp.processCommand("insert 1 fofo baba", table)
        cp.processCommand("insert 2 foo bar", table)

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
        cp.processCommand("insert 1 foo bar", table)
        cp.processCommand("insert 2 baz qaz", table)

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
        cp.processCommand("insert 1 foo bar", table)
        cp.processCommand("insert 2 baz qaz", table)

        val cursor = tableEnd(table)

        assertEquals(2, cursor.cellNum)
        assertEquals(0, cursor.pageNum)
        assertTrue(cursor.endOfTable)
    }

    @Test
    fun `cursor advances correctly`() {
        cp.processCommand("insert 1 foo bar", table)
        cp.processCommand("insert 2 baz qaz", table)

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
        cp.processCommand("insert 10 foo bar", table)
        cp.processCommand("insert 20 baz qaz", table)

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
            cp.processCommand(
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
            cp.processCommand(
                "insert ${it + 1} u${it + 1} e${it + 1}",
                table
            )
        }

        cp.processCommand(
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
            cp.processCommand(
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
            cp.processCommand(
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
            cp.processCommand(
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
            cp.processCommand(
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
            cp.processCommand(
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
            cp.processCommand(
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
            cp.processCommand(
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
            cp.processCommand(
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
            cp.processCommand(
                "insert ${it + 10} u${it + 10} e${it + 10}",
                table
            )
        }

        cp.processCommand("insert 5 u5 e5", table)

        val cursor = tableStart(table)

        assertEquals(5, deserializeRow(cursor).Id)

        cursor.cursorAdvance()

        assertEquals(10, deserializeRow(cursor).Id)
    }


    @Test
    fun `insert into right leaf after root split keeps order`() {
        repeat(LEAF_NODE_MAX_CELLS + 1) {
            cp.processCommand(
                "insert ${it + 1} u${it + 1} e${it + 1}",
                table
            )
        }

        cp.processCommand("insert 100 u100 e100", table)

        val cursor = tableFind(table, 100)

        assertEquals(100, deserializeRow(cursor).Id)
    }


    @Test
    fun `leaf linked list contains all pages after multiple inserts`() {
        repeat(LEAF_NODE_MAX_CELLS * 2 + 1) {
            cp.processCommand(
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
            cp.processCommand(
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
            listOf(1, 2, 5, 10, 15, 20, 25, 30),
            result
        )
    }


    @Test
    fun `find nonexistent key returns insertion position`() {
        repeat(LEAF_NODE_MAX_CELLS + 1) {
            cp.processCommand(
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
        cp.processCommand(
            "insert 1 user email",
            table
        )

            cp.processCommand(
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
            cp.processCommand(
                "insert ${it + 1} u${it + 1} e${it + 1}",
                table
            )
        }

        val root = table.pager.getPage(0)

        val left = getInternalNodeChild(root, 0)
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
            cp.processCommand(
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
            cp.processCommand(
                "insert ${it + 1} u${it + 1} e${it + 1}",
                table
            )
        }

        cp.processCommand(
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
            cp.processCommand(
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
                        getInternalNodeChild(root, it)
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
            cp.processCommand(
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
    fun `dump tree`() {
        repeat(LEAF_NODE_MAX_CELLS * 3 + 1) {
            cp.processCommand(
                "insert ${it + 1} u${it + 1} e${it + 1}",
                table
            )
        }
        dumpTree(table, 0)
    }

    @Test
    fun `delete leaf row`() {
        repeat(5) {
            cp.processCommand(
                "insert ${it + 1} u${it + 1} e${it + 1}",
                table
            )
        }
        cp.processCommand("delete 3", table)
        val node = table.pager.getPage(0)
        assertEquals(4, getLeafNodeNumCells(node))
    }

    @Test
    fun `delete first row in leaf `() {
        repeat(5) {
            cp.processCommand(
                "insert ${it + 1} u${it + 1} e${it + 1}",
                table
            )
        }
        cp.processCommand("delete 1", table)
        val node = table.pager.getPage(0)
        assertEquals(4, getLeafNodeNumCells(node))
    }

    @Test
    fun `delete last row in leaf `() {
        repeat(5) {
            cp.processCommand(
                "insert ${it + 1} u${it + 1} e${it + 1}",
                table
            )
        }
        cp.processCommand("delete 5", table)
        val node = table.pager.getPage(0)
        assertEquals(4, getLeafNodeNumCells(node))
    }

    @Test
    fun `delete several rows in leaf `() {
        val insertCount = 5
        val deleteCount = 3
        repeat(insertCount) {
            cp.processCommand(
                "insert ${it + 1} u${it + 1} e${it + 1}",
                table
            )
        }
        repeat(deleteCount) {
            cp.processCommand("delete ${it + 1}", table)
        }
        val node = table.pager.getPage(0)
        assertEquals(insertCount - deleteCount, getLeafNodeNumCells(node))
    }

    @Test
    fun `delete max row in leaf check parent`() {
        val insertCount = 20
        repeat(insertCount) {
            cp.processCommand(
                "insert ${it + 1} u${it + 1} e${it + 1}",
                table
            )
        }
        cp.processCommand("delete 7", table)
        dumpTree(table, 0)
    }

    private fun scanIds(): List<Int> {
        val ids = mutableListOf<Int>()
        val cursor = tableStart(table)
        while (!cursor.endOfTable) {
            ids += deserializeRow(cursor).Id
            cursor.cursorAdvance()
        }
        return ids
    }

    @Test
    fun `stress delete keeps tree consistent`() {
        val n = 200
        for (i in 1..n) {
            cp.processCommand("insert $i u$i e$i", table)
        }

        val remaining = (1..n).toMutableList()
        val toDelete = (1..n).shuffled(kotlin.random.Random(42))

        for (id in toDelete) {
            cp.processCommand("delete $id", table)
            remaining.remove(id)
            assertEquals(
                remaining.sorted(),
                scanIds(),
                "mismatch after deleting $id"
            )
        }

        assertTrue(scanIds().isEmpty())
    }

    @Test
    fun `delete then reinsert works`() {
        val n = 100
        for (i in 1..n) cp.processCommand("insert $i u$i e$i", table)
        for (i in 1..n) cp.processCommand("delete $i", table)
        assertTrue(scanIds().isEmpty())
        for (i in 1..n) cp.processCommand("insert $i u$i e$i", table)
        assertEquals((1..n).toList(), scanIds())
    }

    @Test
    fun `delete rebalances by borrowing from right`() {
        repeat(LEAF_NODE_MAX_CELLS + 2) {
            cp.processCommand(
                "insert ${it + 1} u${it + 1} e${it + 1}",
                table
            )
        }

        cp.processCommand("delete 7", table)

        val root = table.pager.getPage(table.rootPageNum)

        val left =
            table.pager.getPage(getInternalNodeChild(root, 0))

        val right =
            table.pager.getPage(getRightChild(root))

        assertEquals(7, getLeafNodeNumCells(left))
        assertEquals(7, getLeafNodeNumCells(right))

        val ids = mutableListOf<Int>()

        val cursor = tableStart(table)

        while (!cursor.endOfTable) {
            ids += deserializeRow(cursor).Id
            cursor.cursorAdvance()
        }

        assertEquals(
            listOf(
                1, 2, 3, 4, 5, 6,
                8, 9, 10, 11, 12, 13, 14, 15
            ),
            ids
        )
    }

    @Test
    fun stringTest() {
        val str = "update 15 set values email=email@gmail.com username=ew'ass\"sd"
        val parts = str.split("set")
        val id = parts[0].split(" ").filter { it != "update" }[0].toInt()
        val values = parts[1].split(" ").filter { !it.isEmpty() && it != "values" && it != "," }.map { it.split("=") }
            .associate { it[0] to it[1] }

        println(id)
        println()
        println(values)

    }

    @Test
    fun `update username and email`() {
        cp.processCommand(
            "insert 15 oldUser oldEmail",
            table
        )

        cp.processCommand(
            """update 15 set values email=email@gmail.com username=ew'ass"sd""",
            table
        )

        val cursor = tableFind(table, 15)

        assertEquals(
            Row(
                15,
                """ew'ass"sd""",
                "email@gmail.com"
            ),
            deserializeRow(cursor)
        )
    }

    @Test
    fun `update only email`() {
        cp.processCommand(
            "insert 15 user oldEmail",
            table
        )

        cp.processCommand(
            "update 15 set values email=new@gmail.com",
            table
        )

        val row = deserializeRow(tableFind(table, 15))

        assertEquals(
            Row(
                15,
                "user",
                "new@gmail.com"
            ),
            row
        )
    }

    @Test
    fun `update only username`() {
        cp.processCommand(
            "insert 15 user oldEmail",
            table
        )

        cp.processCommand(
            "update 15 set values username=newUser",
            table
        )

        val row = deserializeRow(tableFind(table, 15))

        assertEquals(
            Row(
                15,
                "newUser",
                "oldEmail"
            ),
            row
        )
    }

    @Test
    fun `select returns updated values`() {
        cp.processCommand(
            "insert 15 user email",
            table
        )

        cp.processCommand(
            "update 15 set values username=newUser email=new@mail.com",
            table
        )

        val cursor = tableStart(table)

        assertEquals(
            Row(
                15,
                "newUser",
                "new@mail.com"
            ),
            deserializeRow(cursor)
        )
    }

    @Test
    fun `help command prints help page`() {
        val out = ByteArrayOutputStream()
        val oldOut = System.out

        System.setOut(PrintStream(out))

        try {
            cp.processCommand(".help", table)

            val output = out.toString()

            assertTrue(output.contains("Help page"))
            assertTrue(output.contains("Insert"))
            assertTrue(output.contains("Delete"))
            assertTrue(output.contains("Update"))
            assertTrue(output.contains("Select"))
            assertTrue(output.contains(".dump_page"))
            assertTrue(output.contains(".dump_tree"))
            assertTrue(output.contains(".q"))
        } finally {
            System.setOut(oldOut)
        }
    }

    @Test
    fun `unknown statement prints error message`() {
        val out = ByteArrayOutputStream()
        val oldOut = System.out

        System.setOut(PrintStream(out))

        try {
            cp.processCommand("abracadabra", table)

            val output = out.toString()

            assertTrue(output.contains("Invalid statement"))
            assertTrue(output.contains("abracadabra"))
            assertTrue(output.contains("Try .help command"))
        } finally {
            System.setOut(oldOut)
        }
    }

    @Test
    fun `validate select succeeds`() {
        assertDoesNotThrow {
            StatementValidator.validate(
                PreparedStatement(
                    "select",
                    StatementStatus.PREPARE_SUCCESS,
                    StatementType.STATEMENT_SELECT,
                    table
                )
            )
        }
    }

    @Test
    fun `validate select rejects invalid syntax`() {
        assertThrows<InvalidPreparedStatementException> {
            StatementValidator.validate(
                PreparedStatement(
                    "select *",
                    StatementStatus.PREPARE_SUCCESS,
                    StatementType.STATEMENT_SELECT,
                    table
                )
            )
        }
    }

    @Test
    fun `validate insert succeeds`() {
        assertDoesNotThrow {
            StatementValidator.validate(
                PreparedStatement(
                    "insert 1 user email",
                    StatementStatus.PREPARE_SUCCESS,
                    StatementType.STATEMENT_INSERT,
                    table
                )
            )
        }
    }

    @Test
    fun `validate insert rejects duplicate id`() {

        cp.processCommand("insert 1 user email", table)

        assertThrows<IdAlreadyExists> {
            StatementValidator.validate(
                PreparedStatement(
                    "insert 1 another another@mail",
                    StatementStatus.PREPARE_SUCCESS,
                    StatementType.STATEMENT_INSERT,
                    table
                )
            )
        }
    }

    @Test
    fun `validate insert rejects invalid id`() {

        assertThrows<InvalidPreparedStatementException> {
            StatementValidator.validate(
                PreparedStatement(
                    "insert abc user email",
                    StatementStatus.PREPARE_SUCCESS,
                    StatementType.STATEMENT_INSERT,
                    table
                )
            )
        }
    }

    @Test
    fun `validate delete succeeds`() {

        cp.processCommand("insert 1 user email", table)

        assertDoesNotThrow {
            StatementValidator.validate(
                PreparedStatement(
                    "delete 1",
                    StatementStatus.PREPARE_SUCCESS,
                    StatementType.STATEMENT_DELETE,
                    table
                )
            )
        }
    }

    @Test
    fun `validate delete rejects missing id`() {

        assertThrows<IdNotFoundException> {
            StatementValidator.validate(
                PreparedStatement(
                    "delete 10",
                    StatementStatus.PREPARE_SUCCESS,
                    StatementType.STATEMENT_DELETE,
                    table
                )
            )
        }
    }

    @Test
    fun `validate delete rejects invalid id`() {

        assertThrows<InvalidPreparedStatementException> {
            StatementValidator.validate(
                PreparedStatement(
                    "delete abc",
                    StatementStatus.PREPARE_SUCCESS,
                    StatementType.STATEMENT_DELETE,
                    table
                )
            )
        }
    }

    @Test
    fun `validate update succeeds`() {

        cp.processCommand("insert 1 user email", table)

        assertDoesNotThrow {
            StatementValidator.validate(
                PreparedStatement(
                    "update 1 set values email=test@gmail.com",
                    StatementStatus.PREPARE_SUCCESS,
                    StatementType.STATEMENT_UPDATE,
                    table
                )
            )
        }
    }

    @Test
    fun `validate update rejects missing row`() {

        assertThrows<IdNotFoundException> {
            StatementValidator.validate(
                PreparedStatement(
                    "update 1 set values email=test@gmail.com",
                    StatementStatus.PREPARE_SUCCESS,
                    StatementType.STATEMENT_UPDATE,
                    table
                )
            )
        }
    }

    @Test
    fun `validate update rejects empty values`() {

        cp.processCommand("insert 1 user email", table)

        assertThrows<InvalidPreparedStatementException> {
            StatementValidator.validate(
                PreparedStatement(
                    "update 1 set values",
                    StatementStatus.PREPARE_SUCCESS,
                    StatementType.STATEMENT_UPDATE,
                    table
                )
            )
        }
    }

    @Test
    fun `validate update rejects missing set`() {

       cp.processCommand("insert 1 user email", table)

        assertThrows<InvalidPreparedStatementException> {
            StatementValidator.validate(
                PreparedStatement(
                    "update 1 email=test",
                    StatementStatus.PREPARE_SUCCESS,
                    StatementType.STATEMENT_UPDATE,
                    table
                )
            )
        }
    }

    @Test
    fun `validate update rejects invalid assignment`() {

        cp.processCommand("insert 1 user email", table)

        assertThrows<InvalidPreparedStatementException> {
            StatementValidator.validate(
                PreparedStatement(
                    "update 1 set values email",
                    StatementStatus.PREPARE_SUCCESS,
                    StatementType.STATEMENT_UPDATE,
                    table
                )
            )
        }
    }

    @Test
    fun `validate update rejects invalid id`() {

        cp.processCommand("insert 1 user email", table)

        assertThrows<InvalidPreparedStatementException> {
            StatementValidator.validate(
                PreparedStatement(
                    "update abc set values email=test",
                    StatementStatus.PREPARE_SUCCESS,
                    StatementType.STATEMENT_UPDATE,
                    table
                )
            )
        }
    }
}
