import com.zxcjabka.game.COLUMN_EMAIL
import com.zxcjabka.game.COLUMN_USERNAME
import com.zxcjabka.game.CommandProcessor
import com.zxcjabka.game.INTERNAL_NODE_MAX_CELLS
import com.zxcjabka.game.StatementExecutor
import com.zxcjabka.game.Table
import com.zxcjabka.game.dbOpen
import com.zxcjabka.game.deserializeRow
import com.zxcjabka.game.flush
import com.zxcjabka.game.tableFind
import com.zxcjabka.game.tableStart
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFalse

class E2ETest {

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
    fun `e2e database persistence after file restart`() {
        val rowsCount = 15
        repeat(rowsCount) {
            cp.processCommand("insert ${it + 1} user${it + 1} email${it + 1}@db.com", table)
        }

        flush(table)
        val dbPath = tempDir.resolve("test.db").toString()
        table.pager.file.close()

        val reOpenedTable = dbOpen(dbPath)

        val cursor = tableStart(reOpenedTable)
        for (id in 1..rowsCount) {
            assertFalse(cursor.endOfTable, "Row $id lost after database restart")
            val row = deserializeRow(cursor)
            assertEquals(id, row.Id)
            assertEquals("user$id", row.Username)
            cursor.cursorAdvance()
        }
        assertTrue(cursor.endOfTable)
    }

    @Test
    fun `e2e maximum length strings constraint handling`() {
        val maxUsername = "u".repeat(COLUMN_USERNAME)
        val maxEmail = "e".repeat(COLUMN_EMAIL)

        cp.processCommand("insert 999 $maxUsername $maxEmail", table)

        val cursor = tableFind(table, 999)
        val row = deserializeRow(cursor)
        assertEquals(999, row.Id)
        assertEquals(maxUsername, row.Username)
        assertEquals(maxEmail, row.Email)
    }

    @Test
    fun `e2e internal node maximum constraint verification`() {
        assertEquals(3, INTERNAL_NODE_MAX_CELLS)
    }

    @Test
    fun `e2e order of elements remains sorted when inserting keys out of order`() {
        val keys = listOf(50, 10, 40, 20, 30)
        keys.forEach { id ->
            cp.processCommand("insert $id user$id mail$id", table)
        }

        val cursor = tableStart(table)
        val expectedSortedKeys = keys.sorted()

        expectedSortedKeys.forEach { expectedId ->
            assertFalse(cursor.endOfTable)
            val row = deserializeRow(cursor)
            assertEquals(expectedId, row.Id)
            cursor.cursorAdvance()
        }
        assertTrue(cursor.endOfTable)
    }
}