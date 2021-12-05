import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
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

	private final int SIZE = 5; // number of rows/columns in square grid
	private final int NUM_POS = SIZE * SIZE; // number of positions in square grid
	private final int NUM_CARDS = Card.NUM_CARDS; // number of cards in deck
	private Random random = new Random(); // pseudorandom number generator for Monte Carlo simulation
	private int[] plays = new int[NUM_POS]; // positions of plays so far (index 0 through numPlays - 1) recorded as
											// integers using row-major indices.
	// row-major indices: play (r, c) is recorded as a single integer r * SIZE + c
	// (See http://en.wikipedia.org/wiki/Row-major_order)
	// From plays index [numPlays] onward, we maintain a list of yet unplayed
	// positions.
	private int numPlays = 0; // number of Cards played into the grid so far
	private PokerSquaresPointSystem system; // point system
	private int depthLimit = 2; // default depth limit for Random Monte Carlo (MC) play
	private Card[][] grid = new Card[SIZE][SIZE]; // grid with Card objects or null (for empty positions)
	private Card[] simDeck = Card.getAllCards(); // a list of all Cards. As we learn the index of cards in the play
													// deck,
													// we swap each dealt card to its correct index. Thus, from index
													// numPlays
													// onward, we maintain a list of undealt cards for MC simulation.
	public String[] suitNames = Card.getSuitNames();
	public String[] rankNames = Card.getRankNames();

	private int[][] legalPlayLists = new int[NUM_POS][NUM_POS]; // stores legal play lists indexed by numPlays (depth)
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
		for (int row = 0; row < SIZE; row++)
			for (int col = 0; col < SIZE; col++)
				grid[row][col] = null;
		// reset numPlays
		numPlays = 0;
		// (re)initialize list of play positions (row-major ordering)
		for (int i = 0; i < NUM_POS; i++)
			plays[i] = i;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see PokerSquaresPlayer#getPlay(Card, long)
	 */
	@Override
	public int[] getPlay(Card card, long millisRemaining, HashMap cardOnRank) {
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
		int cardIndex = numPlays;
		while (!card.equals(simDeck[cardIndex]))
			cardIndex++;
		simDeck[cardIndex] = simDeck[numPlays];
		simDeck[numPlays] = card;

		if (numPlays < 24) { // not the forced last play
			// compute average time per move evaluation
			int remainingPlays = NUM_POS - numPlays; // ignores triviality of last play to keep a conservative margin
														// for game completion
			long millisPerPlay = millisRemaining / remainingPlays; // dividing time evenly with future getPlay() calls
			long millisPerMoveEval = millisPerPlay / remainingPlays; // dividing time evenly across moves now considered
			// copy the play positions (row-major indices) that are empty

			System.arraycopy(plays, numPlays, legalPlayLists[numPlays], 0, remainingPlays);
			double maxAverageScore = Double.NEGATIVE_INFINITY; // maximum average score found for moves so far
			ArrayList<Integer> bestPlays = new ArrayList<Integer>(); // all plays yielding the maximum average score
			for (int i = 0; i < remainingPlays; i++) { // for each legal play position
				int play = legalPlayLists[numPlays][i];
				long startTime = System.currentTimeMillis();
				long endTime = startTime + millisPerMoveEval; // compute when MC simulations should end
				// for (int j=0; j<25; i++)
				// System.out.println(Arrays.toString(legalPlayLists[j]));
				makePlay(card, play / SIZE, play % SIZE, cardOnRank); // play the card at the empty position

				int simCount = 0;
				int scoreTotal = 0;
				while (System.currentTimeMillis() < endTime) { // perform as many MC simulations as possible through the
																// allotted time
					// Perform a Monte Carlo simulation of random play to the depth limit or game
					// end, whichever comes first.
					scoreTotal += simPlay(depthLimit, cardOnRank, play); // accumulate MC simulation scores
					simCount++; // increment count of MC simulations
				}
				undoPlay(cardOnRank); // undo the play under evaluation
				// update (if necessary) the maximum average score and the list of best plays
				double averageScore = (double) scoreTotal / simCount;
				if (averageScore >= maxAverageScore) {
					if (averageScore > maxAverageScore)
						bestPlays.clear();
					bestPlays.add(play);
					maxAverageScore = averageScore;
				}
			}
			System.out.println(Arrays.toString(legalPlayLists[numPlays]));
			int bestPlay = bestPlays.get(random.nextInt(bestPlays.size())); // choose a best play (breaking ties
																			// randomly)
			// update our list of plays, recording the chosen play in its sequential
			// position; all onward from numPlays are empty positions
			int bestPlayIndex = numPlays;
			while (plays[bestPlayIndex] != bestPlay)
				bestPlayIndex++;
			plays[bestPlayIndex] = plays[numPlays];
			plays[numPlays] = bestPlay;
		}

		int[] playPos = { plays[numPlays] / SIZE, plays[numPlays] % SIZE }; // decode it into row and column
		makePlay(card, playPos[0], playPos[1], cardOnRank); // make the chosen play (not undoing this time)
		return playPos; // return the chosen play
	}

	/**
	 * From the chosen play, perform simulated Card draws and random placement
	 * (depthLimit) iterations forward and return the resulting grid score.
	 * 
	 * @param depthLimit - how many simulated random plays to perform
	 * @return resulting grid score after random MC simulation to given depthLimit
	 */
	private int simPlay(int depthLimit, HashMap cardOnRank, int actualCard) {
		if (depthLimit == 0) { // with zero depth limit, return current score
			return system.getScore(grid);
		} else { // up to the non-zero depth limit or to game end, iteratively make the given
					// number of random plays
			int score = Integer.MIN_VALUE;
			int maxScore = Integer.MIN_VALUE;
			int depth = Math.min(depthLimit, NUM_POS - numPlays); // compute real depth limit, taking into account game
																	// end

			for (int d = 0; d < depth; d++) {
				// generate a random card draw
				int c = random.nextInt(NUM_CARDS - numPlays) + numPlays;
				Card card = simDeck[c];
				// choose a random play from the legal plays
				String temp[] = new String[4];
				boolean isCardPlaced = false;
				for (int s = 0; s < suitNames.length; s++) {
					temp[s] = rankNames[card.getRank()] + suitNames[s];
					String tempCard = rankNames[card.getRank()] + suitNames[s];
					int indicesBasedOnRank = (int) cardOnRank.get(rankNames[card.getRank()] + suitNames[s]);

//					If card from temp array is found then apply PPS and place the card
					if (indicesBasedOnRank != -1 && tempCard != card.toString()) {
						isCardPlaced = proximityPostitionPerSimulation(indicesBasedOnRank, cardOnRank, card);
						break;
					} else {
						isCardPlaced = false;
					}
				}

				if (!isCardPlaced) {
					// previous postion place change - PPS
					isCardPlaced = proximityPostitionPerSimulation(actualCard, cardOnRank, card);
				}
				if (!isCardPlaced) {
					// random - for positon remaning
					int remainingPlays = NUM_POS - numPlays;
					System.arraycopy(plays, numPlays, legalPlayLists[numPlays], 0, remainingPlays);
					int c2 = random.nextInt(remainingPlays);
					int play = legalPlayLists[numPlays][c2];
					makePlay(card, play / SIZE, play % SIZE, cardOnRank);
				}
			}
			score = system.getScore(grid);

			// Undo MC plays.
			for (int d = 0; d < depth; d++) {
				undoPlay(cardOnRank);
			}

			return score;
		}

	}

	public void makePlay(Card card, int row, int col, HashMap cardOnRank) {

		int indices = row * SIZE + col;
		cardOnRank.put(card.toString(), indices);

		// match simDeck to event
		int cardIndex = numPlays;
		while (!card.equals(simDeck[cardIndex]))
			cardIndex++;
		simDeck[cardIndex] = simDeck[numPlays];
		simDeck[numPlays] = card;

		// update plays to reflect chosen play in sequence
		grid[row][col] = card;
		int play = row * SIZE + col;
		int j = 0;
		while (plays[j] != play)
			j++;
		plays[j] = plays[numPlays];
		plays[numPlays] = play;

		// increment the number of plays taken
		numPlays++;
	}

	public void undoPlay(HashMap cardOnRank) { // undo the previous play
		numPlays--;
		int play = plays[numPlays];

		cardOnRank.put(grid[play / SIZE][play % SIZE].toString(), -1);

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

	public boolean proximityPostitionPerSimulation(int indicesBasedOnRank, HashMap cardOnRank, Card card) {
//		Main logic PPS

		int row = indicesBasedOnRank / SIZE;
		int col = indicesBasedOnRank % SIZE;

//		PPS formula

		int tcell = row - 1;
		int bcell = row + 1;
		int lcell = col - 1;
		int rcell = col + 1;

		boolean isCardPlaced = false;
		boolean checkNextAdjacentPostion = true;

		if (tcell > 0) {
			int topcell = ((row - 1) * 5) + col;
			boolean cardfound = false;
			for (Object value : cardOnRank.values()) {
				int actualIndicies = (Integer) value;
				if (actualIndicies == topcell) {
					cardfound = true;
					checkNextAdjacentPostion = false;
					break;
				}
			}

			if (!cardfound) {
				int topcellr = topcell / SIZE;
				int topcellc = topcell % SIZE;
				checkNextAdjacentPostion = false;
				makePlay(card, topcellr, topcellc, cardOnRank);
				isCardPlaced = true;
			}
		}
		if (bcell < 5 && checkNextAdjacentPostion) {
			int bottomcell = ((row + 1) * 5) + col;
			boolean cardfound = false;
			for (Object value : cardOnRank.values()) {
				int actualIndicies = (Integer) value;
				if (actualIndicies == bottomcell) {
					cardfound = true;
					checkNextAdjacentPostion = false;
					break;
				} else {
					cardfound = false;
				}
			}

			if (!cardfound) {
				int bottomcellr = bottomcell / SIZE;
				int bottomcellc = bottomcell % SIZE;
				checkNextAdjacentPostion = false;
				makePlay(card, bottomcellr, bottomcellc, cardOnRank);
				isCardPlaced = true;
			}
		}

		if (lcell > 0 && checkNextAdjacentPostion) {
			int leftcell = (row * 5) + (col - 1);
			boolean cardfound = false;
			for (Object value : cardOnRank.values()) {
				int actualIndicies = (Integer) value;
				if (actualIndicies == leftcell) {
					cardfound = true;
					checkNextAdjacentPostion = false;
					break;
				} else {
					cardfound = false;
				}
			}

			if (!cardfound) {
				int leftcellr = leftcell / SIZE;
				int leftcellc = leftcell % SIZE;
				checkNextAdjacentPostion = false;
				makePlay(card, leftcellr, leftcellc, cardOnRank);
				isCardPlaced = true;
			}
		}

		if (rcell < 5 && checkNextAdjacentPostion) {
			int rightcell = (row * 5) + (col + 1);
			boolean cardfound = false;
			for (Object value : cardOnRank.values()) {
				int actualIndicies = (Integer) value;
				if (actualIndicies == rightcell) {
					cardfound = true;
					checkNextAdjacentPostion = false;
					break;
				} else {
					cardfound = false;
				}
			}

			if (!cardfound) {
				int rightcellr = rightcell / SIZE;
				int rightcellc = rightcell % SIZE;
				checkNextAdjacentPostion = false;
				makePlay(card, rightcellr, rightcellc, cardOnRank);
				isCardPlaced = true;
			}
		}

		return isCardPlaced;
	}

	/**
	 * Demonstrate RandomMCPlay with Ameritish point system.
	 * 
	 * @param args (not used)
	 */
	public static void main(String[] args) {
		PokerSquaresPointSystem system = PokerSquaresPointSystem.getBritishPointSystem();
		System.out.println(system);
		new PokerSquares(new RandomMCPlayer(2), system).play(); // play a single game
	}

}
