package com.softwareverde.wow.bingo;

import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.logging.LineNumberAnnotatedLog;
import com.softwareverde.logging.LogLevel;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.IoUtil;
import com.softwareverde.util.StringUtil;
import com.softwareverde.util.Util;

public class Main {
    public static void main(final String[] parameters) {
        Logger.setLog(LineNumberAnnotatedLog.getInstance());
        Logger.setLogLevel(LogLevel.DEBUG);

        final String adminPassword = (parameters.length > 0 ? parameters[0] : "");
        final Integer ticketCost;
        {
            if (parameters.length > 1) {
                ticketCost = Util.parseInt(parameters[1]);
            }
            else {
                ticketCost = 5;
            }
        }
        final Long seed;
        {
            if (parameters.length > 2) {
                seed = Util.parseLong(parameters[2]);
            }
            else {
                seed = Math.abs((long) (Integer.MAX_VALUE * Math.random()));
            }
        }

        final List<String> bingoSquares;
        {
            final String squaresDatContents = StringUtil.bytesToString(IoUtil.getFileContents("data/squares.dat"));
            final String[] squares = squaresDatContents.split("\n");
            final ImmutableListBuilder<String> listBuilder = new ImmutableListBuilder<>(squares.length);
            for (final String string : squares) {
                listBuilder.add(string);
            }
            bingoSquares = listBuilder.build();
        }

        if (! Util.isBlank(adminPassword)) {
            Logger.info("[Password protected]");
        }
        Logger.info("[Ticket Cost " + ticketCost + "]");
        Logger.info("[Seed " + seed + "]");
        Logger.info("[Loaded " + bingoSquares.getCount() + " squares]");

        final BingoServer bingoServer = new BingoServer(bingoSquares, ticketCost, seed, adminPassword);
        bingoServer.start();

        Logger.info("[Listening on port 8080]");
        bingoServer.loop();

        Logger.info("[Exiting]");
        bingoServer.stop();
    }
}
