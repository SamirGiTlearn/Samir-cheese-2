package com.example

import com.example.chess.engine.ChessEngine
import com.example.chess.engine.GameStatus
import com.example.chess.engine.GameTermination
import com.example.chess.engine.PieceColor
import com.example.chess.engine.PieceType
import com.example.chess.engine.Square
import com.example.data.remote.OnlineChessRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChessEngineTest {

    @Test
    fun `initial fen roundtrip preserves exact state`() {
        val pos = ChessEngine.initialPosition()
        assertEquals(ChessEngine.INITIAL_FEN, ChessEngine.toFen(pos))
        assertEquals(20, ChessEngine.legalMoves(pos).size)
    }

    @Test
    fun `scholars mate detects checkmate and SAN hash`() {
        var pos = ChessEngine.initialPosition()
        val ucis = listOf("e2e4", "e7e5", "d1h5", "b8c6", "f1c4", "g8f6", "h5f7")
        var lastSan = ""
        for (uci in ucis) {
            val res = ChessEngine.tryApplyUciMove(pos, uci)
            assertNotNull("Expected legal move $uci", res)
            lastSan = ChessEngine.computeSan(pos, res!!.first, res.second)
            pos = res.second
        }
        assertEquals("Qxf7#", lastSan)
        val status = ChessEngine.evaluateStatus(pos)
        assertTrue(status is GameStatus.Finished)
        val finished = status as GameStatus.Finished
        assertEquals(PieceColor.WHITE, finished.winner)
        assertEquals(GameTermination.CHECKMATE, finished.termination)
    }

    @Test
    fun `stalemate is detected accurately`() {
        // Black king on a8, White queen on b6, White king on c6, Black to move -> Stalemate
        val pos = ChessEngine.fromFen("k7/8/1QK5/8/8/8/8/8 b - - 0 1")
        val status = ChessEngine.evaluateStatus(pos)
        assertTrue(status is GameStatus.Finished)
        val finished = status as GameStatus.Finished
        assertNull(finished.winner)
        assertEquals(GameTermination.STALEMATE, finished.termination)
    }

    @Test
    fun `castling works when clear and blocks when passing through check`() {
        val clearPos = ChessEngine.fromFen("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1")
        val kingSide = ChessEngine.tryApplyMove(clearPos, Square.fromAlgebraic("e1")!!, Square.fromAlgebraic("g1")!!)
        assertNotNull(kingSide)
        assertEquals(PieceType.ROOK, kingSide!!.pieceAt(Square.fromAlgebraic("f1")!!)?.type)

        val queenSide = ChessEngine.tryApplyMove(clearPos, Square.fromAlgebraic("e1")!!, Square.fromAlgebraic("c1")!!)
        assertNotNull(queenSide)
        assertEquals(PieceType.ROOK, queenSide!!.pieceAt(Square.fromAlgebraic("d1")!!)?.type)

        // Black rook on f8 attacks f1, so White cannot castle kingside
        val attackedF1 = ChessEngine.fromFen("5r2/8/8/8/8/8/8/R3K2R w KQ - 0 1")
        val blocked = ChessEngine.tryApplyMove(attackedF1, Square.fromAlgebraic("e1")!!, Square.fromAlgebraic("g1")!!)
        assertNull(blocked)
    }

    @Test
    fun `en passant works and respects horizontal pin`() {
        val epPos = ChessEngine.fromFen("4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 1")
        val afterEp = ChessEngine.tryApplyMove(epPos, Square.fromAlgebraic("e5")!!, Square.fromAlgebraic("d6")!!)
        assertNotNull(afterEp)
        assertNull(afterEp!!.pieceAt(Square.fromAlgebraic("d5")!!))
        assertEquals(PieceColor.WHITE, afterEp.pieceAt(Square.fromAlgebraic("d6")!!)?.color)

        // Horizontal pin: White king a5, White pawn e5, Black pawn d5, Black rook h5 -> e5xd6 leaves Ka5 exposed to Rh5!
        val pinnedEp = ChessEngine.fromFen("4k3/8/8/K2pP2r/8/8/8/8 w - d6 0 1")
        val illegalEp = ChessEngine.tryApplyMove(pinnedEp, Square.fromAlgebraic("e5")!!, Square.fromAlgebraic("d6")!!)
        assertNull(illegalEp)
    }

    @Test
    fun `pawn promotion supports queen rook bishop knight`() {
        val promoPos = ChessEngine.fromFen("7k/4P3/8/8/8/8/8/4K3 w - - 0 1")
        for (type in listOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT)) {
            val next = ChessEngine.tryApplyMove(
                promoPos,
                Square.fromAlgebraic("e7")!!,
                Square.fromAlgebraic("e8")!!,
                promotion = type
            )
            assertNotNull(next)
            assertEquals(type, next!!.pieceAt(Square.fromAlgebraic("e8")!!)?.type)
        }
    }

    @Test
    fun `threefold repetition and fifty move rule draw detection`() {
        val repPos = ChessEngine.initialPosition()
        val key = ChessEngine.repetitionKey(repPos)
        val statusRep = ChessEngine.evaluateStatus(repPos, listOf(key, "other", key, "other2", key))
        assertTrue(statusRep is GameStatus.Finished)
        assertEquals(GameTermination.THREEFOLD_REPETITION, (statusRep as GameStatus.Finished).termination)

        val fiftyPos = ChessEngine.fromFen("4k3/8/8/8/8/8/8/4K2R w - - 100 60")
        val statusFifty = ChessEngine.evaluateStatus(fiftyPos)
        assertTrue(statusFifty is GameStatus.Finished)
        assertEquals(GameTermination.FIFTY_MOVE_RULE, (statusFifty as GameStatus.Finished).termination)
    }

    @Test
    fun `insufficient material and illegal move rejection`() {
        val kvsk = ChessEngine.fromFen("4k3/8/8/8/8/8/8/4K3 w - - 0 1")
        assertTrue(ChessEngine.isInsufficientMaterial(kvsk))

        val kbn = ChessEngine.fromFen("4k3/8/8/8/8/8/8/4K1N1 w - - 0 1")
        assertTrue(ChessEngine.isInsufficientMaterial(kbn))

        // White cannot move Black's pawn or move knight illegally
        val start = ChessEngine.initialPosition()
        assertNull(ChessEngine.tryApplyMove(start, Square.fromAlgebraic("e7")!!, Square.fromAlgebraic("e5")!!))
        assertNull(ChessEngine.tryApplyMove(start, Square.fromAlgebraic("e2")!!, Square.fromAlgebraic("e5")!!))
    }

    @Test
    fun `room invitation links normalize cleanly to room codes`() {
        assertEquals("CM7K9P", OnlineChessRepository.normalizeRoomCode("cm7k9p"))
        assertEquals("CM7K9P", OnlineChessRepository.normalizeRoomCode("https://chessmaster-samir.app/room/cm7k9p"))
        assertEquals("CM7K9P", OnlineChessRepository.normalizeRoomCode("chessmaster://join?code=cm7k9p"))
    }

    @Test
    fun `formatClockMillis formats minutes, tenths below 10s, and zero accurately`() {
        assertEquals("10:00", com.example.ui.components.formatClockMillis(600_000L))
        assertEquals("00:10", com.example.ui.components.formatClockMillis(10_000L))
        assertEquals("0:09.9", com.example.ui.components.formatClockMillis(9_999L))
        assertEquals("0:09.5", com.example.ui.components.formatClockMillis(9_500L))
        assertEquals("0:00.1", com.example.ui.components.formatClockMillis(100L))
        assertEquals("00:00", com.example.ui.components.formatClockMillis(0L))
    }
}
