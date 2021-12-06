import java.util.Collections;
import java.util.HashMap;
import java.util.Stack;

/**
 * FlushPlayer - a simple example implementation of the player interface for
 * PokerSquares that attempts to get flushes in the first four columns. Author:
 * ________, based on code provided by Todd W. Neller and Michael Fleming
 */
public class FlushPlayer implements PokerSquaresPlayer {

	private final int SIZE = 5; // number of rows/columns in square grid
	private final int NUM_POS = SIZE * SIZE; // number of positions in square grid
	private final int NUM_CARDS = Card.NUM_CARDS; // number of cards in deck
	private Card[][] grid = new Card[SIZE][SIZE]; // grid with Card objects or null (for empty positions)

	/*
	 * (non-Javadoc)
	 * 
	 * @see PokerSquaresPlayer#setPointSystem(PokerSquaresPointSystem, long)
	 */
	@Override
	public void setPointSystem(PokerSquaresPointSystem system, long millis) {
		// The FlushPlayer, like the RandomPlayer, does not worry about the scoring
		// system.
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see PokerSquaresPlayer#init()
	 */
	@Override
	public void init() {
		// clear grid
		for (int row = 0; row < SIZE; row++)
			for (int col = 0; col < SIZE; col++)
				grid[row][col] = null;

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see PokerSquaresPlayer#getPlay(Card, long)
	 */
	@Override
	public int[] getPlay(Card card, long millisRemaining) {
		int cardrow = 0;
		int cardcol = 0;

		int cardrank = card.getRank();
		int cardsuit = card.getSuit();

		boolean cardaddedtogrid = false;

		// Assign the cardcol as the cardsuite value
		cardcol = cardsuit;
		System.out.println("cardsuit " + cardsuit);
		// The card is placed on specific suit grid

		for (cardrow = 0; cardrow < SIZE; cardrow++) {
			if (grid[cardrow][cardcol] == null) {
				grid[cardrow][cardcol] = card;
				cardaddedtogrid = true;
				break;
			}
		}

		if (!cardaddedtogrid) {
			// If the card is not added to the specific col then add it to last column
			cardcol = SIZE - 1;
			// The card is placed at column 4
			for (cardrow = 0; cardrow < SIZE; cardrow++) {
				if (grid[cardrow][cardcol] == null) {
					grid[cardrow][cardcol] = card;
					cardaddedtogrid = true;
					break;
				}
			}
		}

		if (!cardaddedtogrid) {
			// If the card not added to the specfic col and col4 then add it to anywhere top
			// most grid

			for (cardrow = 0; cardrow < SIZE; cardrow++) {
				for (cardcol = 0; cardcol < SIZE; cardcol++) {
					// skip the specific suit column and skip the last column
					if (cardcol != cardsuit || cardcol != SIZE - 1) {
						if (grid[cardrow][cardcol] == null) {
							grid[cardrow][cardcol] = card;
							// The card is placed anywhere to the grid
							cardaddedtogrid = true;
							break;
						}
					}
					cardaddedtogrid = false;
					// No place to add the card. Grid is full
				}
				// Break the loop if the card is placed
				if (cardaddedtogrid) {
					break;
				}
			}
		}

		if (!cardaddedtogrid) {
			// No place to add the card. Grid is full
		}

		int[] playPos = { cardrow, cardcol };
		return playPos;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see PokerSquaresPlayer#getName()
	 */
	@Override
	public String getName() {
		return "FlushPlayer";
	}

	/**
	 * Demonstrate FlushPlayer play with British point system.
	 * 
	 * @param args (not used)
	 */
	public static void main(String[] args) {
		PokerSquaresPointSystem system = PokerSquaresPointSystem.getBritishPointSystem();
		System.out.println(system);
		new PokerSquares(new FlushPlayer(), system).play(); // play a single game
	}

}
