package com.softwareverde.wow.bingo;

import com.softwareverde.constable.list.List;

import java.util.HashMap;

public class BingoState {
    protected final Long _seed;
    protected final List<String> _squareLabels;
    protected final HashMap<Integer, Boolean> _markedLabelIndexes = new HashMap<>();
    protected final HashMap<String, BingoGame> _games = new HashMap<>();

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

        _games.put(name, bingoGame);
    }

    public BingoGame getBingoGame(final String name) {
        return _games.get(name);
    }

    public Boolean hasBingoGame(final String name) {
        return _games.containsKey(name);
    }

    public void markLabel(final Integer labelIndex, final Boolean isMarked) {
        _markedLabelIndexes.put(labelIndex, isMarked);
        for (final BingoGame bingoGame : _games.values()) {
            bingoGame.updateBoard(labelIndex, isMarked);
        }
    }

    public Boolean isLabelMarked(final Integer labelIndex) {
        return _markedLabelIndexes.get(labelIndex);
    }

    public List<String> getSquareLabels() {
        return _squareLabels;
    }
}
