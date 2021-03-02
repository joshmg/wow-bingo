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
        Logger.setLogLevel(LogLevel.ON);

        final Long seed;
        {
            if (parameters.length > 0) {
                seed = Util.parseLong(parameters[0]);
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
        Logger.info("[Seed " + seed + "]");
        Logger.info("[Loaded " + bingoSquares.getCount() + " squares]");

        final BingoServer bingoServer = new BingoServer(bingoSquares, seed);
        bingoServer.start();

        Logger.info("[Listening on port 8080]");
        bingoServer.loop();

        Logger.info("[Exiting]");
        bingoServer.stop();
    }
}
