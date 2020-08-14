package com.maxdemarzi.quine;

import java.util.ArrayList;
import java.util.List;

public class Implicant {

    private final long myMSB;
    private final long myLSB;
    private final int myNumVars;
    private final List<Long> minterms;
    private final List<Long> dontcares;

    public Implicant(long newMSB, long newLSB, int numVars) {
        myMSB = newMSB;
        myLSB = newLSB;
        myNumVars = numVars;
        minterms = new ArrayList<>();
        dontcares = new ArrayList<>();
    }

    public Implicant(long minterm, int numVars, boolean dontcare) {
        myMSB = minterm ^ BooleanExpression.maxVal;
        myLSB = BooleanExpression.maxVal & (minterm | (BooleanExpression.maxVal << numVars));
        myNumVars = numVars;
        minterms = new ArrayList<>();
        dontcares = new ArrayList<>();
        if (dontcare)
            dontcares.add(minterm);
        else
            minterms.add(minterm);
    }

    public void printList() {
        System.out.print("Minterms: ");
        for (Long minterm : minterms) {
            System.out.print(minterm + ", ");
        }
        System.out.print("Dontcare: ");
        for (Long dontcare : dontcares) {
            System.out.print(dontcare + ", ");
        }
        System.out.println();
    }

    public void printSB() {
        System.out.println("MSB is " + myMSB + " LSB is " + myLSB);
    }

    public long getMSB() {
        return myMSB;
    }

    public long getLSB() {
        return myLSB;
    }

    public int getNumVars() {
        return myNumVars;
    }

    public List<Long> getMinterms() {
        return minterms;
    }

    public List<Long> getdontcares() {
        return dontcares;
    }

    public void mergeMinterms(List<Long> min1, List<Long> min2, List<Long> dont1, List<Long> dont2) {
        minterms.addAll(min1);
        minterms.addAll(min2);
        dontcares.addAll(dont1);
        dontcares.addAll(dont2);
    }

    public boolean equals(Implicant imp) {
        return (imp.getLSB() == this.myLSB) &&
                (imp.getNumVars() == this.myNumVars) &&
                (imp.getMSB() == this.myMSB);
    }

    public String getVerilogExpression() {
        StringBuilder expr = new StringBuilder();

        expr.append("(");

        boolean first = true;
        for (int i = 0; i < myNumVars; i++) {
            long tempMSB = myMSB & (1 << i);
            long tempLSB = myLSB & (1 << i);
            char alphabetVal = BooleanExpression.alphabet.charAt(i);

            if (Long.bitCount(tempMSB) == 1 && Long.bitCount(tempLSB) == 0) {
                if (first) {
                    first = false;
                } else {
                    expr.append("&");
                }
                expr.append("(~").append(alphabetVal).append(")");
            }
            if (Long.bitCount(tempMSB) == 0 && Long.bitCount(tempLSB) == 1) {
                if (first) {
                    first = false;
                } else {
                    expr.append("&");
                }
                expr.append(alphabetVal);
            }
        }
        expr.append(")");
        return expr.toString();
    }

    public String getPathExpression() {
        StringBuilder expr = new StringBuilder();

        boolean first = true;
        for (int i = 0; i < myNumVars; i++) {
            long tempMSB = myMSB & (1 << i);
            long tempLSB = myLSB & (1 << i);

            if (Long.bitCount(tempMSB) == 1 && Long.bitCount(tempLSB) == 0) {
                if (first) {
                    first = false;
                } else {
                    expr.append("&");
                }
                expr.append(i);
            }
            if (Long.bitCount(tempMSB) == 0 && Long.bitCount(tempLSB) == 1) {
                if (first) {
                    first = false;
                } else {
                    expr.append("&");
                }
                expr.append(i);
            }
        }
        return expr.toString();
    }

}