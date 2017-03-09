package com.mozz.remoteview;

import android.support.annotation.Nullable;

import com.mozz.remoteview.reader.CodeReader;
import com.mozz.remoteview.token.Token;
import com.mozz.remoteview.token.Type;

import java.io.EOFException;


final class Lexer {

    private CodeReader mReader;

    private StringBuilder mBuffer = new StringBuilder();

    private int mLookFor = 0;

    private static final int LK_NOTHING = 1;
    private static final int LK_INNER = 1 << 1;

    // Add for recognize code from Inner Element. If < script > is meet, than mLookForScript==3,
    // otherwise, mLookForScript < 3.
    private int mLookForScript = 0;

    Lexer(CodeReader reader) {
        mReader = reader;

        lookFor(LK_NOTHING);
    }

    @Nullable
    Token scan() throws EOFException, RVSyntaxError {
        this.skipWhiteSpace();

        switch (peek()) {
            case '<':
                mLookForScript = 1;
                lookFor(LK_NOTHING);
                next();
                return Token.obtainToken(Type.LeftAngleBracket, mReader.line(), mReader.column());

            case '"':
                next();
                mLookForScript = 0;
                return scanValue();
            case '>':
                mLookForScript++;
                lookFor(LK_INNER);
                next();
                return Token.obtainToken(Type.RightAngleBracket, mReader.line(), mReader.column());

            case '/':
                mLookForScript = 0;
                next();
                return Token.obtainToken(Type.Slash, mReader.line(), mReader.column());

            case '=':
                mLookForScript = 0;
                next();
                return Token.obtainToken(Type.Equal, mReader.line(), mReader.column());

            case '{':
                return scanCode();
        }

        if (isLookingFor(LK_INNER) && mLookForScript < 3 && peek() != '<') {
            return scanInner();
        }

        if (isDigit(peek()) || peek() == '-') {
            mLookForScript = 0;
            return scanNumber();
        }

        if (isLetter(peek()) || peek() == '_') {
            return scanId();
        }

        throw new RVSyntaxError("unknown token " + peek(), line(), column());
    }

    private Token scanCode() throws EOFException {
        long startColumn = mReader.column();
        long line = mReader.line();
        clearBuf();

        // Handle the inner bracket of lua script.
        // Such that: a = {}
        int scriptBracket = 0;

        next();
        while (true) {

            if (peek() == '{') {
                scriptBracket++;
            } else if (peek() == '}') {
                scriptBracket--;
                if (scriptBracket == -1) {
                    break;
                }
            }

            mBuffer.append(peek());
            next();
        }

        next();
        return Token.obtainToken(Type.Code, mBuffer.toString(), line, startColumn);
    }

    private Token scanNumber() throws EOFException, RVSyntaxError {
        long startColumn = mReader.column();
        long line = mReader.line();
        int v = 0;
        boolean negative = false;
        if (peek() == '-') {
            negative = true;
            next();
        }

        if (!Lexer.isDigit(peek())) {
            throw new Error("Illegal word when reading Number!");
        }

        do {
            v = 10 * v + (peek() - '0');
            next();
        } while (isDigit(peek()));

        if (peek() != '.' && peek() != 'E' && peek() != 'e')
            return Token.obtainToken(Type.Int, negative ? -v : v, line, startColumn);

        double x = v, d = 10;
        if (peek() == '.') {
            for (; ; ) {
                next();
                if (!Lexer.isDigit(peek())) break;

                x = x + (peek() - '0') / d;
                d = d * 10;
            }
        }

        if (peek() == 'e' || peek() == 'E') {
            next();

            if (!Lexer.isDigit(peek()) && peek() != '-') {
                throw new RVSyntaxError("Illegal word when reading Number!", line, startColumn);
            }
            boolean expIsNegative = false;
            if (peek() == '-') {
                expIsNegative = true;
                next();
            }

            int n = 0;
            do {
                n = 10 * n + (peek() - '0');
                next();
            } while (Lexer.isDigit(peek()));

            n = expIsNegative ? -n : n;

            double exp = Math.pow(10, n);
            return Token.obtainToken(Type.Double, negative ? (-x * exp) : (x * exp), line, startColumn);

        } else {
            return Token.obtainToken(Type.Double, negative ? -x : x, line, startColumn);
        }
    }

    private Token scanId() throws EOFException {
        long startColumn = mReader.column();
        long line = mReader.line();

        clearBuf();
        do {
            mBuffer.append(peek());
            next();
        } while (isLetter(peek()) || isDigit(peek()) || peek() == '.');

        String idStr = mBuffer.toString();

        if (idStr.equals(Type.Template.toString().toLowerCase()) || idStr.equals(Type.Body.toString().toLowerCase())) {
            return Token.obtainToken(Type.Template, line, startColumn);
        } else if (idStr.equals(Type.Script.toString().toLowerCase())) {
            mLookForScript++;
            return Token.obtainToken(Type.Script, line, startColumn);
        } else {
            return Token.obtainToken(Type.Id, mBuffer.toString(), line, startColumn);
        }

    }

    private Token scanValue() throws EOFException {
        long startColumn = mReader.column();
        long line = mReader.line();

        clearBuf();

        if (peek() == '"') {
            next();
            return Token.obtainToken(Type.Value, "", line, startColumn);
        }

        do {
            mBuffer.append(peek());
            next();

            // handling the '\"' case
            if (peek() == '\\') {
                next();
                if (peek() != '"') {
                    mBuffer.append('\\');
                }
            } else if (peek() == '"') {
                break;
            }
        } while (true);

        next();

        return Token.obtainToken(Type.Value, mBuffer.toString(), line, startColumn);

    }

    private Token scanInner() throws EOFException {
        long startColumn = mReader.column();
        long line = mReader.line();

        clearBuf();

        do {
            mBuffer.append(peek());
            next();

            if (peek() == '\\') {
                next();
                if (peek() != '<') {
                    mBuffer.append('\\');
                }
            } else if (peek() == '<') {
                break;
            }

            //TODO 考虑其他的情况，这里只会添加一个空格
            if (skipWhiteSpaceInner()) mBuffer.append(' ');

        } while (peek() != '<');

        lookFor(LK_NOTHING);

        char lastChar = mBuffer.charAt(mBuffer.length() - 1);
        if (lastChar == '\n' || lastChar == '\r') {
            mBuffer.deleteCharAt(mBuffer.length() - 1);
        }
        return Token.obtainToken(Type.Inner, mBuffer.toString(), line, startColumn);
    }


    private boolean skipWhiteSpaceInner() throws EOFException {
        boolean meet = false;
        for (; ; ) {
            char ch = peek();
            if (ch == ' ' || ch == '\r' || ch == '\n' || ch == '\t' || ch == '\f' || ch == '\b') {
                if (!meet)
                    meet = true;
                next();
            } else {
                break;
            }
        }

        return meet;
    }


    private void skipWhiteSpace() throws EOFException {
        for (; ; ) {
            char ch = peek();
            if (ch == ' ' || ch == '\r' || ch == '\n' || ch == '\t' || ch == '\f' || ch == '\b') {
                next();
            } else {
                break;
            }
        }
    }

    void close() {
        if (mReader != null) {
            mReader.close();
        }
    }

    long line() {
        return mReader.line();
    }

    long column() {
        return mReader.column();
    }

    private char peek() {
        return mReader.current();
    }

    private void next() throws EOFException {
        this.mReader.nextCh();
    }

    private void lookFor(int status) {
        mLookFor = 0;
        mLookFor |= status;
    }

    private boolean isLookingFor(int status) {
        return (mLookFor & status) != 0;
    }

    private static boolean isDigit(char ch) {
        return ch >= '0' && ch <= '9';
    }

    private static boolean isLetter(char ch) {
        return (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z');
    }

    private void clearBuf() {
        mBuffer.setLength(0);
    }
}