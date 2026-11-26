import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashMap;
import java.util.Map;

/**
 * Ajedrez para dos jugadores en Java + Swing (sin librerías externas).
 *
 * Funcionalidades:
 *  - Tablero 8x8 y 32 piezas
 *  - Selección con el mouse y resaltado de los movimientos posibles
 *  - Captura de piezas
 *  - Jaque (el rey en jaque se marca en rojo) y jaque mate
 *  - Enroque corto y largo
 *  - Captura al paso
 *  - Promoción del peón (el jugador elige la pieza)
 *  - Tablas: rey ahogado, material insuficiente, regla de los 50 movimientos
 *    y triple repetición de posición
 *  - Botón para reiniciar la partida
 */
public class ChessGame extends JPanel {

    static final int SIZE = 80;                                              // lado de cada casilla (px)
    static final String SIMBOLOS = "\u265A\u265B\u265C\u265D\u265E\u265F";   // rey, dama, torre, alfil, caballo, peón
    static final String ORDEN = "kqrbnp";

    // ---------- Estado de la partida ----------
    char[][] b = new char[8][8];     // ' ' = vacía, MAYÚSCULA = blancas, minúscula = negras
    boolean turnoBlancas;
    boolean terminado;

    // Derechos de enroque: B/N = blancas/negras, C/L = corto/largo
    boolean encBC, encBL, encNC, encNL;

    // Casilla "objetivo" de la captura al paso (-1 si no existe)
    int epF, epC;

    int medio;                                       // semimovimientos sin captura ni movimiento de peón
    final Map<String, Integer> repeticiones = new HashMap<>();
    int vecesPosicion;                               // cuántas veces se ha repetido la posición actual

    // Selección con el mouse
    int selF = -1, selC = -1;

    final JLabel estado;                             // etiqueta de texto (turno, jaque, resultado)

    public ChessGame(JLabel estado) {
        this.estado = estado;
        setPreferredSize(new Dimension(SIZE * 8, SIZE * 8));
        reiniciar();
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                clic(e.getY() / SIZE, e.getX() / SIZE);
            }
        });
    }

    // =====================================================================
    //  Inicio / reinicio
    // =====================================================================
    void reiniciar() {
        String fondo = "rnbqkbnr";
        for (int c = 0; c < 8; c++) {
            b[0][c] = fondo.charAt(c);
            b[1][c] = 'p';
            for (int f = 2; f < 6; f++) b[f][c] = ' ';
            b[6][c] = 'P';
            b[7][c] = Character.toUpperCase(fondo.charAt(c));
        }
        turnoBlancas = true;
        terminado = false;
        encBC = encBL = encNC = encNL = true;
        epF = epC = -1;
        medio = 0;
        selF = selC = -1;
        repeticiones.clear();
        vecesPosicion = repeticiones.merge(clave(), 1, Integer::sum);
        actualizarEstado(null);
        repaint();
    }

    // =====================================================================
    //  Utilidades
    // =====================================================================
    boolean esBlanca(char p) {
        return Character.isUpperCase(p);
    }

    boolean dentro(int f, int c) {
        return f >= 0 && f < 8 && c >= 0 && c < 8;
    }

    char[][] copiar(char[][] t) {
        char[][] n = new char[8][8];
        for (int f = 0; f < 8; f++) n[f] = t[f].clone();
        return n;
    }

    boolean caminoLibre(char[][] t, int f1, int c1, int f2, int c2) {
        int pf = Integer.signum(f2 - f1), pc = Integer.signum(c2 - c1);
        int f = f1 + pf, c = c1 + pc;
        while (f != f2 || c != c2) {
            if (t[f][c] != ' ') return false;
            f += pf;
            c += pc;
        }
        return true;
    }

    // =====================================================================
    //  Reglas: movimiento básico, ataques y jaque
    // =====================================================================

    /** Movimiento básico de cada pieza (sin enroque, sin captura al paso, sin mirar el jaque). */
    boolean movimientoBase(char[][] t, int f1, int c1, int f2, int c2) {
        char p = t[f1][c1];
        if (p == ' ' || (f1 == f2 && c1 == c2)) return false;
        char destino = t[f2][c2];
        if (destino != ' ' && esBlanca(destino) == esBlanca(p)) return false;

        int df = f2 - f1, dc = c2 - c1;
        int adf = Math.abs(df), adc = Math.abs(dc);

        switch (Character.toLowerCase(p)) {
            case 'p': {
                int dir = esBlanca(p) ? -1 : 1;
                int inicio = esBlanca(p) ? 6 : 1;
                if (dc == 0 && destino == ' ') {
                    if (df == dir) return true;
                    return f1 == inicio && df == 2 * dir && t[f1 + dir][c1] == ' ';
                }
                return adc == 1 && df == dir && destino != ' ';
            }
            case 'n':
                return (adf == 2 && adc == 1) || (adf == 1 && adc == 2);
            case 'b':
                return adf == adc && caminoLibre(t, f1, c1, f2, c2);
            case 'r':
                return (df == 0 || dc == 0) && caminoLibre(t, f1, c1, f2, c2);
            case 'q':
                return (df == 0 || dc == 0 || adf == adc) && caminoLibre(t, f1, c1, f2, c2);
            case 'k':
                return adf <= 1 && adc <= 1;
            default:
                return false;
        }
    }

    /** ¿Está la casilla (f,c) atacada por las piezas del bando indicado? (no importa lo que haya en ella) */
    boolean casillaAtacada(char[][] t, int f, int c, boolean porBlancas) {
        // Peones: uno blanco en (f+1, c±1) ataca (f,c); uno negro en (f-1, c±1)
        int fp = porBlancas ? f + 1 : f - 1;
        char peon = porBlancas ? 'P' : 'p';
        for (int dc = -1; dc <= 1; dc += 2) {
            int cc = c + dc;
            if (dentro(fp, cc) && t[fp][cc] == peon) return true;
        }

        // Caballos
        char caballo = porBlancas ? 'N' : 'n';
        int[][] saltos = {{1, 2}, {2, 1}, {-1, 2}, {-2, 1}, {1, -2}, {2, -1}, {-1, -2}, {-2, -1}};
        for (int[] s : saltos) {
            int ff = f + s[0], cc = c + s[1];
            if (dentro(ff, cc) && t[ff][cc] == caballo) return true;
        }

        // Rey
        char rey = porBlancas ? 'K' : 'k';
        for (int df = -1; df <= 1; df++) {
            for (int dc = -1; dc <= 1; dc++) {
                if (df == 0 && dc == 0) continue;
                int ff = f + df, cc = c + dc;
                if (dentro(ff, cc) && t[ff][cc] == rey) return true;
            }
        }

        // Piezas de línea: torre/dama (rectas) y alfil/dama (diagonales)
        char torre = porBlancas ? 'R' : 'r';
        char alfil = porBlancas ? 'B' : 'b';
        char dama = porBlancas ? 'Q' : 'q';
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
        for (int i = 0; i < dirs.length; i++) {
            int ff = f + dirs[i][0], cc = c + dirs[i][1];
            while (dentro(ff, cc)) {
                char x = t[ff][cc];
                if (x != ' ') {
                    boolean recta = i < 4;
                    if (x == dama || (recta && x == torre) || (!recta && x == alfil)) return true;
                    break;
                }
                ff += dirs[i][0];
                cc += dirs[i][1];
            }
        }
        return false;
    }

    boolean enJaque(char[][] t, boolean blancas) {
        char rey = blancas ? 'K' : 'k';
        for (int f = 0; f < 8; f++)
            for (int c = 0; c < 8; c++)
                if (t[f][c] == rey) return casillaAtacada(t, f, c, !blancas);
        return false;
    }

    // =====================================================================
    //  Reglas: movimientos legales (incluye enroque y captura al paso)
    // =====================================================================

    boolean enroquePosible(int fila, int c1, int c2) {
        boolean blancas = esBlanca(b[fila][c1]);
        if (fila != (blancas ? 7 : 0) || c1 != 4) return false;

        boolean corto = c2 > c1;
        boolean derecho = blancas ? (corto ? encBC : encBL) : (corto ? encNC : encNL);
        if (!derecho) return false;

        int cTorre = corto ? 7 : 0;
        if (b[fila][cTorre] != (blancas ? 'R' : 'r')) return false;

        // Casillas entre el rey y la torre deben estar vacías
        for (int c = Math.min(c1, cTorre) + 1; c < Math.max(c1, cTorre); c++)
            if (b[fila][c] != ' ') return false;

        // El rey no puede estar en jaque, ni pasar por una casilla atacada, ni terminar en una
        int paso = corto ? 1 : -1;
        for (int i = 0; i <= 2; i++)
            if (casillaAtacada(b, fila, c1 + paso * i, !blancas)) return false;

        return true;
    }

    /** ¿Es legal mover la pieza de (f1,c1) a (f2,c2) en la posición actual? */
    boolean legal(int f1, int c1, int f2, int c2) {
        char p = b[f1][c1];
        if (p == ' ' || esBlanca(p) != turnoBlancas) return false;
        char pl = Character.toLowerCase(p);

        // Enroque: el rey se mueve dos columnas
        if (pl == 'k' && f1 == f2 && Math.abs(c2 - c1) == 2) return enroquePosible(f1, c1, c2);

        // Captura al paso: peón en diagonal hacia la casilla objetivo (vacía)
        boolean alPaso = pl == 'p' && c1 != c2 && b[f2][c2] == ' ' && f2 == epF && c2 == epC;
        if (alPaso) {
            if (Math.abs(c2 - c1) != 1 || f2 - f1 != (esBlanca(p) ? -1 : 1)) return false;
        } else if (!movimientoBase(b, f1, c1, f2, c2)) {
            return false;
        }

        // Simular el movimiento: el propio rey no puede quedar en jaque
        char[][] sim = copiar(b);
        sim[f2][c2] = sim[f1][c1];
        sim[f1][c1] = ' ';
        if (alPaso) sim[f1][c2] = ' ';
        return !enJaque(sim, esBlanca(p));
    }

    boolean tieneMovimientos() {
        for (int f1 = 0; f1 < 8; f1++)
            for (int c1 = 0; c1 < 8; c1++)
                if (b[f1][c1] != ' ' && esBlanca(b[f1][c1]) == turnoBlancas)
                    for (int f2 = 0; f2 < 8; f2++)
                        for (int c2 = 0; c2 < 8; c2++)
                            if (legal(f1, c1, f2, c2)) return true;
        return false;
    }

    boolean hayAlPasoLegal() {
        if (epF == -1) return false;
        int fp = turnoBlancas ? epF + 1 : epF - 1;
        char peon = turnoBlancas ? 'P' : 'p';
        for (int dc = -1; dc <= 1; dc += 2) {
            int cp = epC + dc;
            if (dentro(fp, cp) && b[fp][cp] == peon && legal(fp, cp, epF, epC)) return true;
        }
        return false;
    }

    // =====================================================================
    //  Ejecutar un movimiento
    // =====================================================================
    void ejecutarMovimiento(int f1, int c1, int f2, int c2, char promo) {
        char p = b[f1][c1];
        char pl = Character.toLowerCase(p);
        boolean captura = b[f2][c2] != ' ';

        // Enroque: mover también la torre
        if (pl == 'k' && Math.abs(c2 - c1) == 2) {
            int cTorre = c2 > c1 ? 7 : 0;
            int cDestino = c2 > c1 ? 5 : 3;
            b[f1][cDestino] = b[f1][cTorre];
            b[f1][cTorre] = ' ';
        }

        // Captura al paso: quitar el peón capturado
        if (pl == 'p' && c1 != c2 && !captura) {
            b[f1][c2] = ' ';
            captura = true;
        }

        b[f2][c2] = p;
        b[f1][c1] = ' ';
        if (promo != ' ') b[f2][c2] = promo;       // promoción del peón

        // Actualizar derechos de enroque (rey o torre movidos, o torre capturada)
        if (p == 'K') { encBC = false; encBL = false; }
        if (p == 'k') { encNC = false; encNL = false; }
        if ((f1 == 7 && c1 == 0) || (f2 == 7 && c2 == 0)) encBL = false;
        if ((f1 == 7 && c1 == 7) || (f2 == 7 && c2 == 7)) encBC = false;
        if ((f1 == 0 && c1 == 0) || (f2 == 0 && c2 == 0)) encNL = false;
        if ((f1 == 0 && c1 == 7) || (f2 == 0 && c2 == 7)) encNC = false;

        // Casilla de captura al paso (solo tras un avance doble de peón)
        epF = epC = -1;
        if (pl == 'p' && Math.abs(f2 - f1) == 2) {
            epF = (f1 + f2) / 2;
            epC = c1;
        }

        // Regla de los 50 movimientos
        if (pl == 'p' || captura) medio = 0;
        else medio++;

        turnoBlancas = !turnoBlancas;
        vecesPosicion = repeticiones.merge(clave(), 1, Integer::sum);
    }

    /** Identificador de la posición (para detectar la triple repetición). */
    String clave() {
        StringBuilder sb = new StringBuilder();
        for (char[] fila : b) sb.append(fila);
        sb.append(turnoBlancas ? 'w' : 'b');
        sb.append(encBC ? 1 : 0).append(encBL ? 1 : 0).append(encNC ? 1 : 0).append(encNL ? 1 : 0);
        if (hayAlPasoLegal()) sb.append(epF).append(epC);
        return sb.toString();
    }

    // =====================================================================
    //  Fin de la partida
    // =====================================================================
    boolean materialInsuficiente() {
        int menores = 0, caballos = 0, colorAlfil = -1;
        boolean mismoColor = true;
        for (int f = 0; f < 8; f++) {
            for (int c = 0; c < 8; c++) {
                char p = Character.toLowerCase(b[f][c]);
                if (p == ' ' || p == 'k') continue;
                if (p == 'p' || p == 'r' || p == 'q') return false;
                menores++;
                if (p == 'n') {
                    caballos++;
                } else {
                    int color = (f + c) % 2;
                    if (colorAlfil == -1) colorAlfil = color;
                    else if (colorAlfil != color) mismoColor = false;
                }
            }
        }
        if (menores <= 1) return true;                 // rey solo, o rey + una pieza menor
        return caballos == 0 && mismoColor;            // solo alfiles, todos del mismo color de casilla
    }

    void comprobarFinal() {
        boolean jaque = enJaque(b, turnoBlancas);
        String msg = null;

        if (!tieneMovimientos()) {
            msg = jaque
                    ? "Jaque mate. Ganan las " + (turnoBlancas ? "negras" : "blancas") + "."
                    : "Tablas por rey ahogado.";
        } else if (materialInsuficiente()) {
            msg = "Tablas por material insuficiente.";
        } else if (medio >= 100) {
            msg = "Tablas por la regla de los 50 movimientos.";
        } else if (vecesPosicion >= 3) {
            msg = "Tablas por triple repetición de posición.";
        }

        actualizarEstado(msg);
        if (msg != null) {
            terminado = true;
            selF = selC = -1;
            paintImmediately(0, 0, getWidth(), getHeight());
            JOptionPane.showMessageDialog(this, msg, "Fin de la partida", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    void actualizarEstado(String resultado) {
        if (estado == null) return;
        if (resultado != null) {
            estado.setText(resultado);
        } else {
            estado.setText("Turno: " + (turnoBlancas ? "blancas" : "negras")
                    + (enJaque(b, turnoBlancas) ? "  \u2014  \u00a1JAQUE!" : ""));
        }
    }

    // =====================================================================
    //  Interacción con el mouse
    // =====================================================================
    char elegirPromocion(boolean blancas) {
        String[] opciones = {"Dama", "Torre", "Alfil", "Caballo"};
        char[] letras = {'q', 'r', 'b', 'n'};
        int r = JOptionPane.showOptionDialog(this, "Elige la pieza para la promoci\u00f3n",
                "Promoci\u00f3n del pe\u00f3n", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
                null, opciones, opciones[0]);
        char l = r >= 0 ? letras[r] : 'q';             // si cierra el cuadro, se corona dama
        return blancas ? Character.toUpperCase(l) : l;
    }

    void clic(int f, int c) {
        if (terminado || !dentro(f, c)) return;

        boolean propia = b[f][c] != ' ' && esBlanca(b[f][c]) == turnoBlancas;
        if (propia) {                                   // seleccionar / cambiar de pieza
            selF = f;
            selC = c;
        } else if (selF != -1 && legal(selF, selC, f, c)) {
            char p = b[selF][selC];
            char promo = ' ';
            if (Character.toLowerCase(p) == 'p' && (f == 0 || f == 7)) promo = elegirPromocion(esBlanca(p));
            ejecutarMovimiento(selF, selC, f, c, promo);
            selF = selC = -1;
            repaint();
            comprobarFinal();
            return;
        } else {
            selF = selC = -1;
        }
        repaint();
    }

    // =====================================================================
    //  Dibujo
    // =====================================================================
    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        Font fuentePieza = new Font("Segoe UI Symbol", Font.PLAIN, 58);
        Font fuenteCoord = new Font("SansSerif", Font.BOLD, 12);
        FontMetrics fm = g.getFontMetrics(fuentePieza);

        Color claro = new Color(240, 217, 181);
        Color oscuro = new Color(181, 136, 99);
        boolean jaque = enJaque(b, turnoBlancas);
        char rey = turnoBlancas ? 'K' : 'k';

        for (int f = 0; f < 8; f++) {
            for (int c = 0; c < 8; c++) {
                boolean casillaClara = (f + c) % 2 == 0;
                int x0 = c * SIZE, y0 = f * SIZE;

                // Casilla
                g.setColor(casillaClara ? claro : oscuro);
                if (f == selF && c == selC) g.setColor(new Color(246, 246, 105));
                g.fillRect(x0, y0, SIZE, SIZE);

                // Rey en jaque
                if (jaque && b[f][c] == rey) {
                    g.setColor(new Color(220, 60, 60, 170));
                    g.fillRect(x0, y0, SIZE, SIZE);
                }

                // Movimientos posibles de la pieza seleccionada
                if (!terminado && selF != -1 && legal(selF, selC, f, c)) {
                    g.setColor(new Color(0, 0, 0, 80));
                    if (b[f][c] != ' ') {                       // captura: anillo
                        g.setStroke(new BasicStroke(5));
                        g.drawOval(x0 + 8, y0 + 8, SIZE - 16, SIZE - 16);
                        g.setStroke(new BasicStroke(1));
                    } else {                                    // casilla vacía: punto
                        g.fillOval(x0 + SIZE / 2 - 10, y0 + SIZE / 2 - 10, 20, 20);
                    }
                }

                // Coordenadas (a-h, 1-8)
                g.setFont(fuenteCoord);
                g.setColor(casillaClara ? oscuro : claro);
                if (c == 0) g.drawString(String.valueOf(8 - f), x0 + 4, y0 + 14);
                if (f == 7) g.drawString(String.valueOf((char) ('a' + c)), x0 + SIZE - 12, y0 + SIZE - 5);

                // Pieza
                char p = b[f][c];
                if (p != ' ') {
                    g.setFont(fuentePieza);
                    String s = SIMBOLOS.charAt(ORDEN.indexOf(Character.toLowerCase(p))) + "\uFE0E";
                    int x = x0 + (SIZE - fm.stringWidth(s)) / 2;
                    int y = y0 + (SIZE + fm.getAscent() - fm.getDescent()) / 2;
                    if (esBlanca(p)) {                          // blancas: relleno blanco con contorno negro
                        g.setColor(Color.BLACK);
                        for (int dx = -1; dx <= 1; dx++)
                            for (int dy = -1; dy <= 1; dy++) g.drawString(s, x + dx, y + dy);
                        g.setColor(Color.WHITE);
                    } else {
                        g.setColor(new Color(30, 30, 30));
                    }
                    g.drawString(s, x, y);
                }
            }
        }
    }

    // =====================================================================
    //  Main
    // =====================================================================
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame ventana = new JFrame("Ajedrez");
            ventana.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

            JLabel estado = new JLabel("", SwingConstants.CENTER);
            estado.setFont(new Font("SansSerif", Font.BOLD, 16));

            ChessGame tablero = new ChessGame(estado);

            JButton reiniciar = new JButton("Reiniciar partida");
            reiniciar.addActionListener(e -> tablero.reiniciar());

            JPanel barra = new JPanel(new BorderLayout(10, 0));
            barra.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
            barra.add(estado, BorderLayout.CENTER);
            barra.add(reiniciar, BorderLayout.EAST);

            ventana.setLayout(new BorderLayout());
            ventana.add(tablero, BorderLayout.CENTER);
            ventana.add(barra, BorderLayout.SOUTH);
            ventana.pack();
            ventana.setResizable(false);
            ventana.setLocationRelativeTo(null);
            ventana.setVisible(true);
        });
    }
}
