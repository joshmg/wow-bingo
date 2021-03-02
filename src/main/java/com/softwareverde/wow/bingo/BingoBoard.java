package com.softwareverde.wow.bingo;

import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.json.Json;
import com.softwareverde.json.Jsonable;
import com.softwareverde.util.Tuple;

public class BingoBoard<T> implements Jsonable {
    public static Tuple<Integer, Integer> convertLinearIndexToPosition(final Integer index, final Integer boardWidth) {
        final int x = (index % boardWidth);
        final int y = (index / boardWidth);
        return new Tuple<>(x, y);
    }

    protected final Integer _width;
    protected final Integer _size;
    protected final MutableList<T> _values;

    public BingoBoard(final Integer width, final T initialValue) {
        _width = width;

        _size = (width * width);
        _values = new MutableList<>(_size);
        for (int i = 0; i < _size; ++i) {
            _values.add(initialValue);
        }
    }

    public T getValue(final Integer x, final Integer y) {
        final int index = (x + (y * _width));
        if (index >= _size) { throw new IndexOutOfBoundsException("(" + x + "," + y + ") exceeds size " + _size); }

        return _values.get(index);
    }

    public void setValue(final Integer x, final Integer y, final T value) {
        final int index = (x + (y * _width));
        if (index >= _size) { throw new IndexOutOfBoundsException("(" + x + "," + y + ") exceeds size " + _size); }

        _values.set(index, value);
    }

    public void setValue(final Tuple<Integer, Integer> position, final T value) {
        this.setValue(position.first, position.second, value);
    }

    public Tuple<Integer, Integer> getPositionOf(final T value) {
        final int indexOfValue = _values.indexOf(value);
        if (indexOfValue < 0) { return null; }

        return BingoBoard.convertLinearIndexToPosition(indexOfValue, _width);
    }

    public Integer getWidth() {
        return _width;
    }

    public Integer getSize() {
        return _size;
    }

    @Override
    public Json toJson() {
        final Json json = new Json(false);
        json.put("width", _width);

        final Json values = new Json(true);
        for (final T value : _values) {
            values.add(value);
        }
        json.put("values", values);
        return json;
    }
}
