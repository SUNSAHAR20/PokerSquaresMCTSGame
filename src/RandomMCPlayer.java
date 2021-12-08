import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Random;

/**
 * RandomMCPlayer - a simple Monte Carlo implementation of the player interface
 * for PokerSquares. For each possible play, continues play with random possible
 * card draws and random card placements to a given depth limit (or game end).
 * Having sampled trajectories for all possible plays, the RandomMCPlayer then
 * selects the play yielding the best average scoring potential in such Monte
 * Carlo simulation.
 * 
 * Disclaimer: This example code is not intended as a model of efficiency.
 * (E.g., patterns from Knuth's Dancing Links algorithm (DLX) can provide faster
 * legal move list iteration/deletion/restoration.) Rather, this example code
 * illustrates how a player could be constructed. Note how time is simply
 * managed so as to not run out the play clock.
 * 
 * Author: Todd W. Neller Modifications by: Michael W. Fleming
 */
public class RandomMCPlayer implements PokerSquaresPlayer {

	private final int GRIDSIZE = 5; // number of rows/columns in square grid
	private final int NUM_POS_IN_GRID = GRIDSIZE * GRIDSIZE; // number of positions in square grid
	private final int NUM_CARDS_IN_DECK = Card.NUM_CARDS; // number of cards in deck
	private Random random = new Random(); // pseudorandom number generator for Monte Carlo simulation
	private int[] plays_indices = new int[NUM_POS_IN_GRID]; // positions of plays so far (index 0 through
															// NUM_CARDS_PLAYED_IN_GRID - 1) recorded as
	// integers using row-major indices.
	// row-major indices: play (r, c) is recorded as a single integer r * SIZE + c
	// (See http://en.wikipedia.org/wiki/Row-major_order)
	// From plays index [NUM_CARDS_PLAYED_IN_GRID] onward, we maintain a list of yet
	// unplayed
	// positions.
	private int NUM_CARDS_PLAYED_IN_GRID = 0; // number of Cards played into the grid so far
	private PokerSquaresPointSystem system; // point system
	private int depthLimit = 4; // default depth limit for Random Monte Carlo (MC) play
	private Card[][] pokersGrid = new Card[GRIDSIZE][GRIDSIZE]; // grid with Card objects or null (for empty positions)
	private static Card[] simDeck = Card.getAllCards(); // a list of all Cards. As we learn the index of cards in the
														// play
	// deck,
	// we swap each dealt card to its correct index. Thus, from index
	// NUM_CARDS_PLAYED_IN_GRID
	// onward, we maintain a list of undealt cards for MC simulation.
	public static String[] suitNames = Card.getSuitNames();
	public static String[] rankNames = Card.getRankNames();
	private static HashMap<String, Integer> CARD_INDICES_ON_RANK = new HashMap<String, Integer>();

	static {

		// create mapping from String representations to Card objects
		for (Card card : simDeck)
			CARD_INDICES_ON_RANK.put(card.toString(), -1);
	}

	private int[][] possibleGridPos = new int[NUM_POS_IN_GRID][NUM_POS_IN_GRID]; // stores legal play lists indexed by
																					// NUM_CARDS_PLAYED_IN_GRID (depth)
	// (This avoids constant allocation/deallocation of such lists during the
	// selections of MC simulations.)

	/**
	 * Create a Random Monte Carlo player that simulates random play to depth 2.
	 */
	public RandomMCPlayer() {
	}

	/**
	 * Create a Random Monte Carlo player that simulates random play to a given
	 * depth limit.
	 * 
	 * @param depthLimit depth limit for random simulated play
	 */
	public RandomMCPlayer(int depthLimit) {
		this.depthLimit = depthLimit;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see PokerSquaresPlayer#init()
	 */
	@Override
	public void init() {
		// clear grid
		for (int row = 0; row < GRIDSIZE; row++)
			for (int col = 0; col < GRIDSIZE; col++)
				pokersGrid[row][col] = null;
		// reset NUM_CARDS_PLAYED_IN_GRID
		NUM_CARDS_PLAYED_IN_GRID = 0;
		// (re)initialize list of play positions (row-major ordering)
		for (int i = 0; i < NUM_POS_IN_GRID; i++)
			plays_indices[i] = i;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see PokerSquaresPlayer#getPlay(Card, long)
	 */
	@Override
	public int[] getPlay(Card card, long millisRemaining) {
		/*
		 * With this algorithm, the player chooses the legal play that has the highest
		 * expected score outcome. This outcome is estimated as follows: For each move,
		 * many simulated random plays to the set depthLimit are performed and the
		 * (sometimes partially-filled) grid is scored. For each play simulation, random
		 * undrawn cards are drawn in simulation and the player picks a play position
		 * randomly. After many such plays, the average score per simulated play is
		 * computed. The play with the highest average score is chosen (breaking ties
		 * randomly).
		 */

		// match simDeck to actual play event; in this way, all indices forward from the
		// card contain a list of
		// undealt Cards in some permutation.
		int cardIndex = NUM_CARDS_PLAYED_IN_GRID;
		while (!card.equals(simDeck[cardIndex]))
			cardIndex++;
		simDeck[cardIndex] = simDeck[NUM_CARDS_PLAYED_IN_GRID];
		simDeck[NUM_CARDS_PLAYED_IN_GRID] = card;

		//Place the selected card diagonally
		if (NUM_CARDS_PLAYED_IN_GRID < 5) {
			int[] playPos = { plays_indices[NUM_CARDS_PLAYED_IN_GRID] / GRIDSIZE,
					plays_indices[NUM_CARDS_PLAYED_IN_GRID] % GRIDSIZE }; // decode it into row and column
			playPos[0] = NUM_CARDS_PLAYED_IN_GRID;
			playPos[1] = NUM_CARDS_PLAYED_IN_GRID;
			makePlay(card, playPos[0], playPos[1]); // make the chosen play (not undoing this time)
			int indices = playPos[0] * GRIDSIZE + playPos[1];

			CARD_INDICES_ON_RANK.put(card.toString(), indices);
			return playPos; // return the chosen play
		} else if (NUM_CARDS_PLAYED_IN_GRID < 24) { // not the forced last play
			// compute average time per move evaluation
			int remainingPlays = NUM_POS_IN_GRID - NUM_CARDS_PLAYED_IN_GRID; // ignores triviality of last play to keep
																				// a conservative margin
			// for game completion
			long millisPerPlay = millisRemaining / remainingPlays; // dividing time evenly with future getPlay() calls
			long millisPerMoveEval = millisPerPlay / remainingPlays; // dividing time evenly across moves now considered
			// copy the play positions (row-major indices) that are empty

			System.arraycopy(plays_indices, NUM_CARDS_PLAYED_IN_GRID, possibleGridPos[NUM_CARDS_PLAYED_IN_GRID], 0,
					remainingPlays);
			double maxAverageScore = Double.NEGATIVE_INFINITY; // maximum average score found for moves so far
			ArrayList<Integer> bestPlays = new ArrayList<Integer>(); // all plays yielding the maximum average score
			for (int i = 0; i < remainingPlays; i++) { // for each legal play position
				int play = possibleGridPos[NUM_CARDS_PLAYED_IN_GRID][i];
				long startTime = System.currentTimeMillis();
				long endTime = startTime + millisPerMoveEval; // compute when MC simulations should end

				makePlay(card, play / GRIDSIZE, play % GRIDSIZE); // play the card at the empty position
				int simCount = 0;
				int scoreTotal = 0;
				while (System.currentTimeMillis() < endTime) { // perform as many MC simulations as possible through the
					// allotted time
					// Perform a Monte Carlo simulation of random play to the depth limit or game
					// end, whichever comes first.
					scoreTotal += simPlay(depthLimit, play); // accumulate MC simulation scores
					simCount++; // increment count of MC simulations
				}
				undoPlay(); // undo the play under evaluation
				// update (if necessary) the maximum average score and the list of best plays
				double averageScore = (double) scoreTotal / simCount;
				if (averageScore >= maxAverageScore) {
					if (averageScore > maxAverageScore)
						bestPlays.clear();
					bestPlays.add(play);
					maxAverageScore = averageScore;
				}
			}
			System.out.println(Arrays.toString(possibleGridPos[NUM_CARDS_PLAYED_IN_GRID]));
			int bestPlay = bestPlays.get(random.nextInt(bestPlays.size())); // choose a best play (breaking ties
																			// randomly)
			// update our list of plays, recording the chosen play in its sequential
			// position; all onward from NUM_CARDS_PLAYED_IN_GRID are empty positions
			int bestPlayIndex = NUM_CARDS_PLAYED_IN_GRID;
			while (plays_indices[bestPlayIndex] != bestPlay)
				bestPlayIndex++;
			plays_indices[bestPlayIndex] = plays_indices[NUM_CARDS_PLAYED_IN_GRID];
			plays_indices[NUM_CARDS_PLAYED_IN_GRID] = bestPlay;
		}

		int[] playPos = { plays_indices[NUM_CARDS_PLAYED_IN_GRID] / GRIDSIZE,
				plays_indices[NUM_CARDS_PLAYED_IN_GRID] % GRIDSIZE }; // decode it into row and column

		makePlay(card, playPos[0], playPos[1]); // make the chosen play (not undoing this time)
		int indices = playPos[0] * GRIDSIZE + playPos[1];

		CARD_INDICES_ON_RANK.put(card.toString(), indices);

		return playPos; // return the chosen play
	}

	/**
	 * From the chosen play, perform simulated Card draws and random placement
	 * (depthLimit) iterations forward and return the resulting grid score.
	 * 
	 * @param depthLimit - how many simulated random plays to perform
	 * @return resulting grid score after random MC simulation to given depthLimit
	 */
	private int simPlay(int depthLimit, int actualCard) {
		if (depthLimit == 0) { // with zero depth limit, return current score
			return system.getScore(pokersGrid);
		} else { // up to the non-zero depth limit or to game end, iteratively make the given
					// number of random plays
			int score = Integer.MIN_VALUE;
			int depth = Math.min(depthLimit, NUM_POS_IN_GRID - NUM_CARDS_PLAYED_IN_GRID); // compute real depth limit,
																							// taking into account game
			// end

			for (int d = 0; d < depth; d++) {
				// generate a random card draw
				int c = random.nextInt(NUM_CARDS_IN_DECK - NUM_CARDS_PLAYED_IN_GRID) + NUM_CARDS_PLAYED_IN_GRID;
				Card card = simDeck[c];
				// choose a random play from the legal plays
				String temp[] = new String[4];
				boolean isCardPlaced = false;
				for (int s = 0; s < suitNames.length; s++) {
					String tempCard = rankNames[card.getRank()] + suitNames[s];
					int indicesBasedOnRank = (int) CARD_INDICES_ON_RANK.get(rankNames[card.getRank()] + suitNames[s]);

					// Check if the card picked is having the same rank as already placed in grid					
					if (indicesBasedOnRank != -1 && tempCard != card.toString()) {
						isCardPlaced = proximityPostitionPerSimulation(indicesBasedOnRank, card);
						break;
					} else {
						isCardPlaced = false;
					}

				}

				if (!isCardPlaced) {
					// If same rank card is not postioned. Then place the selected card randmonly
					int remainingPlays = NUM_POS_IN_GRID - NUM_CARDS_PLAYED_IN_GRID;
					System.arraycopy(plays_indices, NUM_CARDS_PLAYED_IN_GRID, possibleGridPos[NUM_CARDS_PLAYED_IN_GRID],
							0, remainingPlays);
					int c2 = random.nextInt(remainingPlays);
					int play = possibleGridPos[NUM_CARDS_PLAYED_IN_GRID][c2];
					makePlay(card, play / GRIDSIZE, play % GRIDSIZE);
				}
			}
			score = system.getScore(pokersGrid);

			// Undo MC plays.
			for (int d = 0; d < depth; d++) {
				undoPlay();
			}

			return score;
		}

	}

	public void makePlay(Card card, int row, int col) {

		int indices = row * GRIDSIZE + col;
		CARD_INDICES_ON_RANK.put(card.toString(), indices);

		// match simDeck to event
		int cardIndex = NUM_CARDS_PLAYED_IN_GRID;
		while (!card.equals(simDeck[cardIndex]))
			cardIndex++;
		simDeck[cardIndex] = simDeck[NUM_CARDS_PLAYED_IN_GRID];
		simDeck[NUM_CARDS_PLAYED_IN_GRID] = card;

		// update plays to reflect chosen play in sequence
		pokersGrid[row][col] = card;
		int play = row * GRIDSIZE + col;
		int j = 0;
		while (plays_indices[j] != play)
			j++;
		plays_indices[j] = plays_indices[NUM_CARDS_PLAYED_IN_GRID];
		plays_indices[NUM_CARDS_PLAYED_IN_GRID] = play;

		// increment the number of plays taken
		NUM_CARDS_PLAYED_IN_GRID++;
	}

	public void undoPlay() { // undo the previous play
		NUM_CARDS_PLAYED_IN_GRID--;
		int play = plays_indices[NUM_CARDS_PLAYED_IN_GRID];

		CARD_INDICES_ON_RANK.put(pokersGrid[play / GRIDSIZE][play % GRIDSIZE].toString(), -1);

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see PokerSquaresPlayer#setPointSystem(PokerSquaresPointSystem, long)
	 */
	@Override
	public void setPointSystem(PokerSquaresPointSystem system, long millis) {
		this.system = system;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see PokerSquaresPlayer#getName()
	 */
	@Override
	public String getName() {
		return "RandomMCPlayerDepth" + depthLimit;
	}
	
//	‘2P’ (Proximity Positioning) playout policy
	public boolean proximityPostitionPerSimulation(int indicesBasedOnRank, Card card) {
		int row = indicesBasedOnRank / GRIDSIZE;
		int col = indicesBasedOnRank % GRIDSIZE;


		int tcell = row - 1;
		int bcell = row + 1;
		int lcell = col - 1;
		int rcell = col + 1;

		boolean isCardPlaced = false;
		boolean checkNextAdjacentPostion = true;
		
        // Check if the topcell for a same rank card is placed
		if (tcell > 0) {
			int topcell = ((row - 1) * 5) + col;
			boolean cardfound = false;
			for (Object value : CARD_INDICES_ON_RANK.values()) {
				int actualIndicies = (Integer) value;
				if (actualIndicies == topcell) {
					cardfound = true;
					checkNextAdjacentPostion = true;
					break;
				}
			}

			if (!cardfound) {
				int topcellr = topcell / GRIDSIZE;
				int topcellc = topcell % GRIDSIZE;
				checkNextAdjacentPostion = false;
				makePlay(card, topcellr, topcellc);
				isCardPlaced = true;
			}
		}
		// Check if the bottomcell for a same rank card is placed
		if (bcell < 5 && checkNextAdjacentPostion) {
			int bottomcell = ((row + 1) * 5) + col;
			boolean cardfound = false;
			for (Object value : CARD_INDICES_ON_RANK.values()) {
				int actualIndicies = (Integer) value;
				if (actualIndicies == bottomcell) {
					cardfound = true;
					checkNextAdjacentPostion = true;
					break;
				} else {
					cardfound = false;
				}
			}

			if (!cardfound) {
				int bottomcellr = bottomcell / GRIDSIZE;
				int bottomcellc = bottomcell % GRIDSIZE;
				checkNextAdjacentPostion = false;
				makePlay(card, bottomcellr, bottomcellc);
				isCardPlaced = true;
			}
		}
		// Check if the leftcell for a same rank card is placed
		if (lcell > 0 && checkNextAdjacentPostion) {
			int leftcell = (row * 5) + (col - 1);
			boolean cardfound = false;
			for (Object value : CARD_INDICES_ON_RANK.values()) {
				int actualIndicies = (Integer) value;
				if (actualIndicies == leftcell) {
					cardfound = true;
					checkNextAdjacentPostion = true;
					break;
				} else {
					cardfound = false;
				}
			}

			if (!cardfound) {
				int leftcellr = leftcell / GRIDSIZE;
				int leftcellc = leftcell % GRIDSIZE;
				checkNextAdjacentPostion = false;
				makePlay(card, leftcellr, leftcellc);
				isCardPlaced = true;
			}
		}
		// Check if the rightcell for a same rank card is placed
		if (rcell < 5 && checkNextAdjacentPostion) {
			int rightcell = (row * 5) + (col + 1);
			boolean cardfound = false;
			for (Object value : CARD_INDICES_ON_RANK.values()) {
				int actualIndicies = (Integer) value;
				if (actualIndicies == rightcell) {
					cardfound = true;
					checkNextAdjacentPostion = true;
					break;
				} else {
					cardfound = false;
				}
			}

			if (!cardfound) {
				int rightcellr = rightcell / GRIDSIZE;
				int rightcellc = rightcell % GRIDSIZE;
				checkNextAdjacentPostion = false;
				makePlay(card, rightcellr, rightcellc);
				isCardPlaced = true;
			}
		}

		return isCardPlaced;
	}

	/**
	 * Demonstrate RandomMCPlay with British point system.
	 * 
	 * @param args (not used)
	 */
	public static void main(String[] args) {
		PokerSquaresPointSystem system = PokerSquaresPointSystem.getBritishPointSystem();
		System.out.println(system);
		new PokerSquares(new RandomMCPlayer(4), system).play(); // play a single game
	}

}