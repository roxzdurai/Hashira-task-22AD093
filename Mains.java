import java.io.*;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.atomic.*;

public class Mains {
    static final class Fraction {
        final BigInteger num;
        final BigInteger den;
        Fraction(BigInteger num, BigInteger den) {
            if (den.signum() == 0) throw new ArithmeticException("den=0");
            if (den.signum() < 0) { num = num.negate(); den = den.negate(); }
            BigInteger g = num.gcd(den);
            if (!g.equals(BigInteger.ONE)) { num = num.divide(g); den = den.divide(g); }
            this.num = num; this.den = den;
        }
        static Fraction of(BigInteger n) { return new Fraction(n, BigInteger.ONE); }
        Fraction add(Fraction o) {
            return new Fraction(this.num.multiply(o.den).add(o.num.multiply(this.den)),
                                this.den.multiply(o.den));
        }
        Fraction sub(Fraction o) {
            return new Fraction(this.num.multiply(o.den).subtract(o.num.multiply(this.den)),
                                this.den.multiply(o.den));
        }
        Fraction mul(Fraction o) {
            return new Fraction(this.num.multiply(o.num), this.den.multiply(o.den));
        }
        Fraction div(Fraction o) {
            if (o.num.signum() == 0) throw new ArithmeticException("div0");
            return new Fraction(this.num.multiply(o.den), this.den.multiply(o.num));
        }
        boolean isInteger() { return den.equals(BigInteger.ONE); }
        BigInteger toIntegerExact() {
            if (!isInteger()) throw new ArithmeticException("not integral: " + this);
            return num;
        }
    }

    static Fraction lagrangeAtZero(long[] xs, BigInteger[] ys) {
        int k = xs.length;
        Fraction sum = Fraction.of(BigInteger.ZERO);
        for (int i = 0; i < k; i++) {
            Fraction term = Fraction.of(ys[i]);
            for (int j = 0; j < k; j++) if (i != j) {
                BigInteger num = BigInteger.valueOf(-xs[j]);
                BigInteger den = BigInteger.valueOf(xs[i] - xs[j]);
                term = term.mul(new Fraction(num, den));
            }
            sum = sum.add(term);
        }
        return sum;
    }

    static Fraction lagrangeAtX(long[] xs, BigInteger[] ys, long xq) {
        int k = xs.length;
        Fraction sum = Fraction.of(BigInteger.ZERO);
        for (int i = 0; i < k; i++) {
            Fraction term = Fraction.of(ys[i]);
            for (int j = 0; j < k; j++) if (i != j) {
                BigInteger num = BigInteger.valueOf(xq - xs[j]);
                BigInteger den = BigInteger.valueOf(xs[i] - xs[j]);
                term = term.mul(new Fraction(num, den));
            }
            sum = sum.add(term);
        }
        return sum;
    }

    static void combinations(int n, int k, CombConsumer consumer) {
        int[] idx = new int[k];
        for (int i = 0; i < k; i++) idx[i] = i;
        while (true) {
            consumer.accept(idx);
            int i = k - 1;
            while (i >= 0 && idx[i] == i + n - k) i--;
            if (i < 0) break;
            idx[i]++;
            for (int j = i + 1; j < k; j++) idx[j] = idx[j - 1] + 1;
        }
    }
    interface CombConsumer { void accept(int[] idx); }

    static class Share {
        long x;
        int base;
        String valueStr;
        BigInteger y;
    }

    static class InputData {
        int n;
        int k;
        List<Share> shares = new ArrayList<>();
    }

    static InputData parseInput(String all) {
        InputData data = new InputData();
        String s = all.replace("\r", "");
        data.n = extractInt(s, "\"n\"\\s*:\\s*(\\d+)");
        data.k = extractInt(s, "\"k\"\\s*:\\s*(\\d+)");
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(
            "\"(\\d+)\"\\s*:\\s*\\{\\s*\"base\"\\s*:\\s*\"(\\d+)\"\\s*,\\s*\"value\"\\s*:\\s*\"([^\"]+)\"\\s*\\}",
            java.util.regex.Pattern.DOTALL
        );
        java.util.regex.Matcher m = p.matcher(s);
        int skipped = 0;
        while (m.find()) {
            Share sh = new Share();
            sh.x = Long.parseLong(m.group(1));
            sh.base = Integer.parseInt(m.group(2));
            sh.valueStr = m.group(3).trim().toLowerCase();
            try {
                sh.y = new BigInteger(sh.valueStr, sh.base);
                data.shares.add(sh);
            } catch (NumberFormatException e) {
                skipped++;
                System.err.println(
                    "Skipping invalid share: x=" + sh.x +
                    " base=" + sh.base + " value='" + sh.valueStr + "'"
                );
            }
        }
        if (data.shares.size() < data.k) {
            throw new RuntimeException(
                "Not enough valid shares after skipping invalid ones: have " +
                data.shares.size() + ", need k=" + data.k
            );
        }
        if (skipped > 0) {
            System.err.println("Total skipped invalid shares: " + skipped);
        }
        return data;
    }

    static int extractInt(String s, String regex) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(regex).matcher(s);
        if (!m.find()) throw new RuntimeException("Missing key: " + regex);
        return Integer.parseInt(m.group(1));
    }

    public static void main(String[] args) throws Exception {
        String input = readAll(System.in);
        InputData data = parseInput(input);
        data.shares.sort(Comparator.comparingLong(a -> a.x));
        int n = data.shares.size();
        int k = data.k;
        long[] Xall = new long[n];
        BigInteger[] Yall = new BigInteger[n];
        for (int i = 0; i < n; i++) {
            Xall[i] = data.shares.get(i).x;
            Yall[i] = data.shares.get(i).y;
        }
        AtomicInteger bestAgreeCount = new AtomicInteger(-1);
        AtomicReference<BigInteger> bestSecret = new AtomicReference<>(null);
        AtomicReference<boolean[]> bestAgreeMask = new AtomicReference<>(null);
        combinations(n, k, idx -> {
            long[] xs = new long[k];
            BigInteger[] ys = new BigInteger[k];
            for (int i = 0; i < k; i++) { xs[i] = Xall[idx[i]]; ys[i] = Yall[idx[i]]; }
            Fraction f0 = lagrangeAtZero(xs, ys);
            if (!f0.isInteger()) return;
            boolean[] ok = new boolean[n];
            int agree = 0;
            for (int t = 0; t < n; t++) {
                Fraction fy = lagrangeAtX(xs, ys, Xall[t]);
                if (fy.isInteger() && fy.toIntegerExact().equals(Yall[t])) {
                    ok[t] = true; agree++;
                } else {
                    ok[t] = false;
                }
            }
            if (agree > bestAgreeCount.get()) {
                bestAgreeCount.set(agree);
                bestSecret.set(f0.toIntegerExact());
                bestAgreeMask.set(ok);
            }
        });
        if (bestSecret.get() == null) {
            System.out.println("secret: UNDETERMINED");
            System.out.println("bad_shares: ALL");
            System.out.println("accuracy: 0 / " + n + " (0.00%)");
            return;
        }
        List<Long> badXs = new ArrayList<>();
        boolean[] mask = bestAgreeMask.get();
        for (int i = 0; i < n; i++) if (!mask[i]) badXs.add(Xall[i]);
        System.out.println("secret: " + bestSecret.get());
        if (badXs.isEmpty()) {
            System.out.println("bad_shares: []");
        } else {
            System.out.print("bad_shares: [");
            for (int i = 0; i < badXs.size(); i++) {
                if (i > 0) System.out.print(", ");
                System.out.print(badXs.get(i));
            }
            System.out.println("]");
        }
        int correctShares = bestAgreeCount.get();
        double percent = (100.0 * correctShares) / n;
        System.out.printf("accuracy: %d / %d (%.2f%%)%n", correctShares, n, percent);
    }

    static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int r;
        while ((r = in.read(buf)) != -1) bos.write(buf, 0, r);
        return bos.toString("UTF-8");
    }
}
