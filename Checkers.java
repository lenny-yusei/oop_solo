public class Checkers extends SimpleGame {
	public Checkers(String[] args) {
		super(args);
	}
	public void play(Board board) {
		System.out.println("遊び方：動かしたいコマのマス番号と、移動先のマス番号を繋げて入力してください.");
		System.out.println("例：　縦6, 横3のマスにあるコマを縦5, 横4のマスに動かしたい -> 入力=" + '"' + 6253 + '"');
		System.out.println();
		super.play(board);
	}
	public static void main(String[] args) {
        (new Checkers(args)).play(new CheckersBoard());
    }
}