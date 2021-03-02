package com.softwareverde.wow.bingo;

import com.softwareverde.json.Json;
import com.softwareverde.json.Jsonable;
import com.softwareverde.util.Tuple;

import java.util.HashSet;
import java.util.Random;

public class BingoGame implements Jsonable {
    public static final Integer BOARD_WIDTH = 5;

    protected final Long _seed;
    protected final BingoBoard<Integer> _boardLayout;
    protected final BingoBoard<Boolean> _boardValues;

    public BingoGame(final Integer uniqueSquareCount, final Long seed) {
        _seed = seed;

        final int boardWidth = BOARD_WIDTH;
        _boardLayout = new BingoBoard<>(boardWidth, 0);
        _boardValues = new BingoBoard<>(boardWidth, false);
        final int boardSize = _boardValues.getSize();

        if (uniqueSquareCount < boardSize) {
            throw new RuntimeException("Unable to create board of size " + boardSize + " with only " + uniqueSquareCount + " defined squares.");
        }

        final HashSet<Integer> consumedSquares = new HashSet<>(boardSize);
        final Random random = new Random(seed);

        for (int i = 0; i < boardSize; ++i) {
            Integer index;
            do {
                index = (Math.abs(random.nextInt()) % uniqueSquareCount);
            } while(consumedSquares.contains(index));
            consumedSquares.add(index);

            final Tuple<Integer, Integer> position = BingoBoard.convertLinearIndexToPosition(i, boardWidth);
            _boardLayout.setValue(position, index);
        }
    }

    public void updateBoard(final Integer squareValue, final Boolean isMarked) {
        final Tuple<Integer, Integer> position = _boardLayout.getPositionOf(squareValue);
        if (position == null) { return; }

        _boardValues.setValue(position, isMarked);
    }

    public Boolean isABingo() {
        { // Check for horizontal bingo...
            for (int y = 0; y < BOARD_WIDTH; ++y) {
                int setCount = 0;
                for (int x = 0; x < BOARD_WIDTH; ++x) {
                    final Boolean isSet = _boardValues.getValue(x, y);
                    if (! isSet) { break; }

                    setCount += 1;
                }
                if (setCount >= BOARD_WIDTH) { return true; }
            }
        }

        { // Check for vertical bingo...
            for (int x = 0; x < BOARD_WIDTH; ++x) {
                int setCount = 0;
                for (int y = 0; y < BOARD_WIDTH; ++y) {
                    final Boolean isSet = _boardValues.getValue(x, y);
                    if (! isSet) { break; }

                    setCount += 1;
                }
                if (setCount >= BOARD_WIDTH) { return true; }
            }
        }

        { // Check for top-left diagonal bingo...
            int setCount = 0;
            for (int i = 0; i < BOARD_WIDTH; ++i) {
                final Boolean isSet = _boardValues.getValue(i, i);
                if (! isSet) { break; }

                setCount += 1;
            }
            if (setCount >= BOARD_WIDTH) { return true; }
        }

        { // Check for top-right diagonal bingo...
            int setCount = 0;
            for (int i = BOARD_WIDTH; i > 0; --i) {
                final int x = (i - 1);              // ..., 3, 2, 1, 0
                final int y = (BOARD_WIDTH - i);    // 0, 1, 2, 3, ...
                final Boolean isSet = _boardValues.getValue(x, y);
                if (! isSet) { break; }

                setCount += 1;
            }
            if (setCount >= BOARD_WIDTH) { return true; }
        }

        return false;
    }

    @Override
    public Json toJson() {
        final Json json = new Json(false);
        json.put("seed", _seed);
        json.put("layout", _boardLayout);
        json.put("marks", _boardValues);
        return json;
    }
}
