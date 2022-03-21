package com.softwareverde.wow.bingo;

import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableList;
import com.softwareverde.constable.list.mutable.MutableList;

import java.util.HashMap;

public class BingoState {
    protected final Long _seed;
    protected final Integer _ticketCost;
    protected final List<String> _squareLabels;
    protected final HashMap<Integer, Boolean> _markedLabelIndexes = new HashMap<>();
    protected final HashMap<String, BingoGame> _bingoGames = new HashMap<>();
    protected final HashMap<String, Boolean> _playersPaid = new HashMap<>();
    protected final MutableList<String> _playersWon = new MutableList<>();

    protected String _getPlayer(final BingoGame bingoGame) {
        for (final String playerName : _bingoGames.keySet()) {
            final BingoGame otherBingoGame = _bingoGames.get(playerName);
            if (bingoGame == otherBingoGame) { // NOTE: Intentional reference equality...
                return playerName;
            }
        }
        return null;
    }

    public BingoState(final List<String> squareLabels, final Integer ticketCost, final Long seed) {
        _ticketCost = ticketCost;
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
        _playersPaid.put(name, false);
    }

    public BingoGame getBingoGame(final String name) {
        return _bingoGames.get(name);
    }

    public Boolean hasBingoGame(final String name) {
        return _bingoGames.containsKey(name);
    }

    public void markLabel(final Integer labelIndex, final Boolean isMarked) {
        _markedLabelIndexes.put(labelIndex, isMarked);
        for (final BingoGame bingoGame : _bingoGames.values()) {
            final int previousBingoCount = bingoGame.getBingoCount();
            bingoGame.updateBoard(labelIndex, isMarked);
            final int newBingoCount = bingoGame.getBingoCount();

            if (newBingoCount > previousBingoCount) {
                final String playerName = _getPlayer(bingoGame);
                if (playerName != null) {
                    _playersWon.add(playerName);
                }
            }
        }
    }

    public List<String> getWinningBingoUsers() {
        return _playersWon.asConst();
    }

    public Boolean isLabelMarked(final Integer labelIndex) {
        return _markedLabelIndexes.get(labelIndex);
    }

    public List<String> getSquareLabels() {
        return _squareLabels;
    }

    public List<String> getPlayers() {
        return new ImmutableList<>(_bingoGames.keySet());
    }

    public void setHasPaid(final String playerName, final Boolean hasPaid) {
        _playersPaid.put(playerName, hasPaid);
    }

    public Boolean hasPaid(final String playerName) {
        return _playersPaid.get(playerName);
    }

    public Long getJackpot(final Integer winnerIndex) {
        int paidUserCount = 0;
        for (final String player : _playersPaid.keySet()) {
            final Boolean hasPaid = _playersPaid.get(player);
            if (hasPaid) {
                paidUserCount += 1;
            }
        }

        final int totalJackpot = (_ticketCost * paidUserCount);
        final double divisor = Math.pow(2.0, (winnerIndex + 1));
        return (long) (totalJackpot / divisor);
    }

    public Long getJackpot() {
        final int winnerCount = _playersWon.getCount();
        return this.getJackpot(winnerCount);
    }
}
