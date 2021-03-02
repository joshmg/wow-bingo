package com.softwareverde.wow.bingo;

import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;

import java.util.HashMap;

public class BingoState {
    protected final Long _seed;
    protected final List<String> _squareLabels;
    protected final HashMap<Integer, Boolean> _markedLabelIndexes = new HashMap<>();
    protected final HashMap<String, BingoGame> _bingoGames = new HashMap<>();

    public BingoState(final List<String> squareLabels, final Long seed) {
        _seed = seed;
        _squareLabels = squareLabels.asConst();

        int index = 0;
        for (final String label : _squareLabels) {
            _markedLabelIndexes.put(index, false);
            index += 1;
        }
    }

    public void newBingoGame(final String name) {
        final Integer labelCount = _squareLabels.getCount();
        final Long seed = Math.abs(_seed + name.hashCode());
        final BingoGame bingoGame = new BingoGame(labelCount, seed);

        for (final Integer markedLabelIndex : _markedLabelIndexes.keySet()) {
            final Boolean isMarked = _markedLabelIndexes.get(markedLabelIndex);
            bingoGame.updateBoard(markedLabelIndex, isMarked);
        }

        _bingoGames.put(name, bingoGame);
    }

    public BingoGame getBingoGame(final String name) {
        return _bingoGames.get(name);
    }

    public Boolean hasBingoGame(final String name) {
        return _bingoGames.containsKey(name);
    }

    public List<String> markLabel(final Integer labelIndex, final Boolean isMarked) {
        final MutableList<String> winningBingoGames = new MutableList<>();

        _markedLabelIndexes.put(labelIndex, isMarked);
        for (final BingoGame bingoGame : _bingoGames.values()) {
            bingoGame.updateBoard(labelIndex, isMarked);
            if (bingoGame.isABingo()) {
                for (final String username : _bingoGames.keySet()) {
                    final BingoGame matchingBingoGame = _bingoGames.get(username);
                    if (bingoGame == matchingBingoGame) { // NOTE: Intentional instance-equals.
                        winningBingoGames.add(username);
                        break;
                    }
                }
            }
        }

        return winningBingoGames;
    }

    public List<String> getWinningBingos() {
        final MutableList<String> winningBingoGames = new MutableList<>();

        for (final BingoGame bingoGame : _bingoGames.values()) {
            if (bingoGame.isABingo()) {
                for (final String username : _bingoGames.keySet()) {
                    final BingoGame matchingBingoGame = _bingoGames.get(username);
                    if (bingoGame == matchingBingoGame) { // NOTE: Intentional instance-equals.
                        winningBingoGames.add(username);
                        break;
                    }
                }
            }
        }

        return winningBingoGames;
    }

    public Boolean isLabelMarked(final Integer labelIndex) {
        return _markedLabelIndexes.get(labelIndex);
    }

    public List<String> getSquareLabels() {
        return _squareLabels;
    }
}
