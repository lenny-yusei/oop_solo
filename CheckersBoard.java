import java.util.*;
import java.io.PrintStream;

// チェッカーをプレイするためのクラス ルールはイギリス式チェッカーのものを採用した
public class CheckersBoard extends AbstractBoard {
	// ゲームで扱うコマのクラス
	class Piece {
		Player.ID playerID;			// そのコマを所有するプレイヤー
		boolean canCapture;			// このコマが相手のコマを取ることができるならtrue
		boolean isKing;				// このコマがKingに成っているならtrue
		int crownedTurn;			// このコマがKingに成った時のターンを記憶する
		Piece(Player.ID playerID) {
			this.playerID = playerID;
			canCapture = false;
			isKing = false;
			crownedTurn = 0;
		}
	}
	
	Piece[] pieces;					// 使用するコマは配列で管理する
	int[] board;					// (x,y)座標をx+8yに配置した１次元配列を使う
									// (x,y)のコマはpieces[board[8*x+y]-1]に置かれる　board[idx]==0なら空である
	int turnCount;					// ターン数を記憶する
	Deque<Integer> takenPieces;		// 過去にとられたコマの番号を順に追加する
	Deque<Integer> continuing;		// 自分のターンが続く場合、動かせるコマの座標を格納　違うなら-1を格納する
	
	CheckersBoard() {
		int i;
		pieces = new Piece[12 * 2];
		for (i = 0; i < 12; i++)
			pieces[i] = new Piece(Player.ID.P1);
		for (i = 12; i < 24; i++)
			pieces[i] = new Piece(Player.ID.P2);
		
		board = new int[8 * 8];
		for (i = 0; i < 64; i++)
			board[i] = 0;
		
		int count = 1;
		for (i = 64 - 1; i >= 40; i--) {
			if (((i/8) + (i%8)) % 2 == 1)
				board[i] = count++;
		}
		for (i = 24 - 1; i >= 0; i--) {
			if (((i/8) + (i%8)) % 2 == 1)
				board[i] = count++;
		}
		takenPieces = new LinkedList<Integer>();
		continuing = new LinkedList<Integer>(); continuing.push(-1);
		turnCount = 1;
	}
	
	// 片方の駒がすべてない、またはプレイヤーの打つ手がないならtrue
	public boolean isEndOfGame() {
		if (alivePieses(Player.ID.P1) == 0 || alivePieses(Player.ID.P2) == 0)
			return true;
		List<Integer> list = legalMoves();
		return list.isEmpty();
	}
	// 全てのコマを取られたプレイヤーもしくは動けなくなった今の手番のプレイヤーを返す
    public Player.ID winner() {
		if (alivePieses(Player.ID.P2) == 0)
			return Player.ID.P1;
		else if (alivePieses(Player.ID.P1) == 0)
			return Player.ID.P2;
		return nextTurn;
	}
	// board のインデックスからPieceを取得する
	Piece getPiece(int idx) {
		if (board[idx] > 0)
			return pieces[board[idx] - 1];
		else
			return null;
	}
	// board のインデックスからplayerIDを取得する
	Player.ID getPlayerID(int idx) {
		return (board[idx] <= 0)? Player.ID.NONE : (board[idx] <= 12)? Player.ID.P1 : Player.ID.P2;
	}
	// プレイヤーpの残っているコマの数
	int alivePieses(Player.ID p) {
		int count = 0;
		for (int i = 0; i < 64; i++) {
			if (getPlayerID(i) == p) count++;
		}
		return count;
	}
	// 自分のターンが続くならtrue
	boolean continueTurn() {
		return continuing.getFirst() >= 0;
	}
	// 配列のインデックスからプレイヤーの手の値に変換する
	int pos2Move(int pos) {
		return 10 * (pos >> 3) + (pos & 0x7) + 11;
	}
	// プレイヤーの手の値から配列のインデックスに変換する
	int move2Pos(int move) {
		move -= 11;
		return (move / 10 << 3) + (move % 10);
	}
	//　board[a]とboard[b]の二次元空間上での横方向の距離を返す　どちらかがboardの外にあるなら-1を返す
	int distance(int a, int b) {
		return (0 <= a && a < 64 && 0 <= b && b < 64)? Math.abs((a & 0x7) - (b & 0x7)) : -1;
	}
	
	// board[idx]のコマがvector方向に取ることができるコマがあるならtrue
	boolean canCapture2(int idx, int vector) {
		return distance(idx, idx + 2*vector) == 2				// 移動先の座標が適切
			&& getPlayerID(idx + vector) == opposite()			// 取る相手のコマがあるかどうか
			&& getPlayerID(idx + 2*vector) == Player.ID.NONE;	// 移動先が開いているかどうか
	}
	// board[idx]のコマが相手のコマを取ることができるならtrue
	boolean canCapture(int idx) {
		boolean result = false;
		Piece piece = getPiece(idx);
		if (piece.playerID == Player.ID.P1 || piece.isKing) { 	// 上方向
			result |= canCapture2(idx, -7);	// 右上
			result |= canCapture2(idx, -9);	//　左上
		}
		if (piece.playerID == Player.ID.P2 || piece.isKing) {	// 下方向
			result |= canCapture2(idx, 9);	// 右下
			result |= canCapture2(idx, 7);	//　左下
		}
		return result;
	}
	// 自分のコマにcanCaptureの真偽を与え、どれか1つでもtureだったならtrueを返す
	boolean giveCapture() {
		for (Piece piece : pieces)
			piece.canCapture = false;
		boolean x = false;
		for (int i = 0; i < 64; i++) {
			if (getPlayerID(i) != nextTurn)
				continue;
			Piece piece = getPiece(i);
			piece.canCapture = canCapture(i);
			x |= piece.canCapture;
		}
		return x;
	}
	// list にboard[idx]のコマが相手のコマをとる操作を加える
	void addCaptureMoves(List<Integer> list, int idx) {
		Piece piece = getPiece(idx);
		if (piece.playerID == Player.ID.P1 || piece.isKing) {	// 上方向
			if (canCapture2(idx, -7))	// 右上
				list.add(pos2Move(idx) * 100 + pos2Move(idx - 2*7));
			if (canCapture2(idx, -9))	// 左上
				list.add(pos2Move(idx) * 100 + pos2Move(idx - 2*9));
		}
		if (piece.playerID == Player.ID.P2 || piece.isKing) {	// 下方向
			if (canCapture2(idx, 9))		// 右下
				list.add(pos2Move(idx) * 100 + pos2Move(idx + 2*9));
			if (canCapture2(idx, 7))		// 左下
				list.add(pos2Move(idx) * 100 + pos2Move(idx + 2*7));
		}
	}
	
	// board[idx]のコマがvector方向に移動することができるならtrue
	boolean canMove2(int idx, int vector) {
		return distance(idx, idx + vector) == 1 && getPlayerID(idx + vector) == Player.ID.NONE;
	}
	// board[idx]のコマの移動を格納する
	void addMoves(List<Integer> list, int idx) {
		Piece piece = getPiece(idx);
		if (piece.playerID == Player.ID.P1 || piece.isKing) {	// 上方向
			if (canMove2(idx, -7))	// 右上
				list.add(pos2Move(idx) * 100 + pos2Move(idx - 7));
			if (canMove2(idx, -9))	// 左上
				list.add(pos2Move(idx) * 100 + pos2Move(idx - 9));
		}
		if (piece.playerID == Player.ID.P2 || piece.isKing) {	// 下方向
			if (canMove2(idx, 9))	// 右下
				list.add(pos2Move(idx) * 100 + pos2Move(idx + 9));
			if (canMove2(idx, 7))	// 左下
				list.add(pos2Move(idx) * 100 + pos2Move(idx + 7));
		}
	}
	
    public List<Integer> legalMoves() {
		List<Integer> result = new LinkedList<Integer>();
		// 自分のターンが続いてるならそのコマからの動きを格納する
		if (continueTurn()) {
			addCaptureMoves(result, continuing.getFirst());
		}
		// 相手のコマを取ることができるなら必ずそのコマをとる
		else if (giveCapture()) {
			for (int i = 0; i < 64; i++) {
				if (getPlayerID(i) != nextTurn() || !getPiece(i).canCapture)
					continue;
				addCaptureMoves(result, i);
			}
		}
		//　コマの動きを格納する
		else {
			for (int i = 0; i < 64; i++) {
				if (getPlayerID(i) != nextTurn)
					continue;
				addMoves(result, i);
			}
		}			
		return result;
	}
	
	// board[]をPiece[]に変換して渡す
	public Object boardState() {
		Piece[] _pieces = new Piece[8 * 8];
		for (int i = 0; i < 8*8; i++)
			_pieces[i] = getPiece(i);
		return _pieces;
	}
    
	public void put(int m) {
		history.push(m);
		int pa = move2Pos(m / 100);	// 移動元
		int pb = move2Pos(m % 100);	// 移動先
		board[pb] = board[pa];
		board[pa] = 0;
		if ((pa + pb) % 2 == 0) { 	// 2マス開けて移動している、つまり相手のコマを取った場合 そのコマを盤上から除外する
			int px = (pa + pb) / 2;
			takenPieces.push(board[px]);
			board[px] = 0;
			// もしこのコマがさらに相手のコマを取ることができるなら自分のターンを続ける
			if (canCapture(pb))
				continuing.push(pb);
			else
				continuing.push(-1);
		}
		else 
			continuing.push(-1);
		// もしこのコマが相手側の辺にたどり着いたとき、このコマはKingに成る
		Piece piece = getPiece(pb);
		if (!piece.isKing && 
			(piece.playerID == Player.ID.P1 && pb / 8 == 0 || piece.playerID == Player.ID.P2 && pb / 8 == 7)) {
				piece.isKing = true;
				piece.crownedTurn = turnCount;
		}
		turnCount++;
		if (!continueTurn())
			flipTurn();
	}
	public void unput() {
		if (!continueTurn())
			flipTurn();
		turnCount--;
		int m = history.pop();
		int pa = move2Pos(m / 100);	// 本来の移動元
		int pb = move2Pos(m % 100);	// 本来の移動先
		board[pa] = board[pb];
		board[pb] = 0;
		if ((pa + pb) % 2 == 0) { 	// 2マス開けて移動している、つまり相手のコマを取った場合　そのコマを盤上に戻す
			int px = (pa + pb) / 2;
			board[px] = takenPieces.pop();
		}
		continuing.pop();
		Piece piece = getPiece(pa);
		if (turnCount == piece.crownedTurn) {
			piece.isKing = false;
			piece.crownedTurn = 0;
		}
	}
	
    public void print(PrintStream out) {
		out.println("    (1) (2) (3) (4) (5) (6) (7) (8)");
        for (int y = 0; y < 8; y++) {
			out.print("(" + (y+1) + ") ");
            for (int x = 0; x < 8; x++) {
                int num = x + y * 8;
				Piece p = getPiece(num);
                if (p == null) out.print("   ");
				else {
					char c = (p.playerID == Player.ID.P1)? (p.isKing)? '@':'O' : (p.isKing)? '%':'X';
					out.print(" " + c + " ");
				}
                if (x < 7) {
                    out.print("|");
                }
            }
            out.println();
            if (y < 7) {
                out.println("    ---+---+---+---+---+---+---+---");
            }
        }
		List<Integer> moves = legalMoves();
		out.print("選択可能な手: ");
		for (int m : moves)
			out.print(m + " ");
		out.println();
    }
}
