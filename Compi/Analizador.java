
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;


public class Analizador {
    private static final List<String> palabrasReservadas = List.of("si", "fin", "for", "impr", "int", "cad", "do", "while");
    private static final List<String> combinacionesPosibles = List.of("bfhjk", "ifbfhjk", "elsebfhjk", "forbfhjk", "printbfhjk", "intbfhjk");
    private static final Pattern identi = Pattern.compile("^[a-zA-Z][a-zA-Z0-9.]*$"); //empieza con mayuscula o minuscula y puede contener letras y numeros
    private static final Pattern pconstante = Pattern.compile("^[0-9]+$"); //expresion regular para constante numericas 
    private static final Pattern poperador = Pattern.compile("^(\\+|-|\\*|/|:=|>=|<=|>|<|=|<>|\\{|\\}|\\[|\\]|\\(|\\)|,|:|;)$"); //operadores del lenguaje, e incluye los de 
    //operaciones artemeticas

    public static void main(String[] args) {
        List<Token> tokens = new ArrayList<>();
        List<ErrorLexico> errores = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader("C:\\Users\\emibr\\OneDrive\\Desktop\\codigo_fuente2.txt"))) {
            String linea;
            while ((linea = reader.readLine()) != null) {
                linea = eliminarComentarios(linea);
                List<Token> tokensLinea = tokenizar(linea);
                tokens.addAll(tokensLinea);
                errores.addAll(filtrarErrores(tokensLinea));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Imprimir tokens y errores léxicos
        for (Token token : tokens) {
            System.out.println(token);
        }

        System.out.println("\nErrores léxicos:");
        for (ErrorLexico error : errores) {
            System.out.println(error);
        }

        // Realizar análisis sintáctico
        Parser parser = new Parser(tokens);
        Nodo raiz = parser.parsear();

        // Imprimir el árbol de análisis sintáctico (AST)
        if (raiz != null) {
            System.out.println("\n----Arbol de análisis sintáctico:-----");
            recorrerArbol(raiz, 0);
        } else {
            System.out.println("Error en el análisis sintáctico: no se pudo construir el árbol");
        }

    }

    private static void recorrerArbol(Nodo nodo, int nivel) {
        if (nodo != null) {
            String indentacion = "  ".repeat(nivel);
            System.out.println(indentacion + nodo);
    
            for (Nodo hijo : nodo.hijos) {
                recorrerArbol(hijo, nivel + 1);
            }
        }
    }

    // Clase para representar los nodos del árbol de análisis sintáctico (AST)
    private static class Nodo {
        private TipoNodo tipo;
        private String valor;
        private List<Nodo> hijos;

        public Nodo(TipoNodo tipo, String valor) {
            this.tipo = tipo;
            this.valor = valor;
            this.hijos = new ArrayList<>();
        }

        public void agregarHijo(Nodo hijo) {
            hijos.add(hijo);
        }

        @Override
        public String toString() {
            return tipo + ": " + valor;
        }
    }

    private enum TipoNodo {
        Programa, Declaracion, Sentencia, Asignacion, Condicion, Expresion, Termino, Factor, Identificador, Constante, Operador
    }

    // Clase Parser para realizar el análisis sintáctico
    private static class Parser {
        private List<Token> tokens;
        private int indice;

        public Parser(List<Token> tokens) {
            this.tokens = tokens;
            this.indice = 0;
        }

        public Nodo parsear() {
            return programa();
        }

        private Nodo programa() {
            Nodo nodo = new Nodo(TipoNodo.Programa, "Programa");
            nodo.hijos.addAll(declaraciones());
            nodo.hijos.addAll(sentencias());
            return nodo;
        }

        private List<Nodo> declaraciones() {
            List<Nodo> declaraciones = new ArrayList<>();
            while (indice < tokens.size() && (tokens.get(indice).tipo == TipoToken.PalabraReservada && (tokens.get(indice).valor.equals("int") || tokens.get(indice).valor.equals("cad")))) {
                declaraciones.add(declaracion());
            }
            return declaraciones;
        }

        private Nodo declaracion() {
            Nodo nodo = new Nodo(TipoNodo.Declaracion, tokens.get(indice).valor);
            indice++; // Consumir la palabra reservada "int" o "cad"
            nodo.agregarHijo(new Nodo(TipoNodo.Identificador, tokens.get(indice).valor));
            indice++; // Consumir el identificador
            consumirToken(TipoToken.OPERADOR, ";"); // Consumir el punto y coma
            return nodo;
        }

        private List<Nodo> sentencias() {
            List<Nodo> sentencias = new ArrayList<>();
            while (indice < tokens.size()) {
                sentencias.add(sentencia());
            }
            return sentencias;
        }

        private Nodo sentencia() {
            if (tokens.get(indice).tipo == TipoToken.PalabraReservada) {
                switch (tokens.get(indice).valor) {
                    case "si":
                        return sentenciaCondicional();
                    case "for":
                        return sentenciaCiclo();
                    case "impr":
                        return sentenciaImprimir();
                    case "while":
                        return sentenciaCicloWhile();
                    default:
                        // Manejar error sintáctico: palabra reservada no reconocida
                        return null;
                }
            } else {
                return asignacion();
            }
        }

        private Nodo asignacion() {
            Nodo nodo = new Nodo(TipoNodo.Asignacion, "Asignación");
            nodo.agregarHijo(new Nodo(TipoNodo.Identificador, tokens.get(indice).valor));
            indice++; // Consumir el identificador
            consumirToken(TipoToken.OPERADOR, ":="); // Consumir el operador de asignación
            nodo.agregarHijo(expresion());
            consumirToken(TipoToken.OPERADOR, ";"); // Consumir el punto y coma
            return nodo;
        }

        private Nodo sentenciaCondicional() {
            Nodo nodo = new Nodo(TipoNodo.Sentencia, "Sentencia Condicional");
            consumirToken(TipoToken.PalabraReservada, "si"); // Consumir la palabra reservada "si"
            nodo.agregarHijo(condicion());
            consumirToken(TipoToken.COMBINACION, "bfhjk"); // Consumir la combinación "bfhjk"
            nodo.hijos.addAll(sentencias());
            if (indice < tokens.size() && tokens.get(indice).tipo == TipoToken.COMBINACION && tokens.get(indice).valor.equals("elsebfhjk")) {
                indice++; // Consumir la combinación "elsebfhjk"
                nodo.hijos.addAll(sentencias());
            }
            consumirToken(TipoToken.PalabraReservada, "fin"); // Consumir la palabra reservada "fin"
            return nodo;
        }

        private Nodo sentenciaCiclo() {
            Nodo nodo = new Nodo(TipoNodo.Sentencia, "Sentencia de Ciclo");
            consumirToken(TipoToken.PalabraReservada, "for"); // Consumir la palabra reservada "for"
            nodo.agregarHijo(asignacion());
            consumirToken(TipoToken.PalabraReservada, "to"); // Consumir la palabra reservada "to"
            nodo.agregarHijo(expresion());
            consumirToken(TipoToken.COMBINACION, "bfhjk"); // Consumir la combinación "bfhjk"
            nodo.hijos.addAll(sentencias());
            consumirToken(TipoToken.PalabraReservada, "fin"); // Consumir la palabra reservada "fin"
            return nodo;
                    }
            
                    private Nodo sentenciaImprimir() {
                        Nodo nodo = new Nodo(TipoNodo.Sentencia, "Sentencia Imprimir");
                        consumirToken(TipoToken.PalabraReservada, "impr"); // Consumir la palabra reservada "impr"
                        nodo.agregarHijo(expresion());
                        consumirToken(TipoToken.OPERADOR, ";"); // Consumir el punto y coma
                        return nodo;
                    }
            
                    private Nodo sentenciaCicloWhile() {
                        Nodo nodo = new Nodo(TipoNodo.Sentencia, "Sentencia de Ciclo While");
                        consumirToken(TipoToken.PalabraReservada, "while"); // Consumir la palabra reservada "while"
                        nodo.agregarHijo(condicion());
                        consumirToken(TipoToken.COMBINACION, "bfhjk"); // Consumir la combinación "bfhjk"
                        nodo.hijos.addAll(sentencias());
                        consumirToken(TipoToken.PalabraReservada, "fin"); // Consumir la palabra reservada "fin"
                        return nodo;
                    }
            
                    private Nodo condicion() {
                        Nodo nodo = new Nodo(TipoNodo.Condicion, "Condición");
                        nodo.agregarHijo(expresion());
                        nodo.agregarHijo(new Nodo(TipoNodo.Operador, tokens.get(indice).valor));
                        indice++; // Consumir el operador relacional
                        nodo.agregarHijo(expresion());
                        return nodo;
                    }
            
                    private Nodo expresion() {
                        Nodo nodo = new Nodo(TipoNodo.Expresion, "Expresión");
                        nodo.agregarHijo(termino());
                        while (indice < tokens.size() && (tokens.get(indice).tipo == TipoToken.OPERADOR && (tokens.get(indice).valor.equals("+") || tokens.get(indice).valor.equals("-")))) {
                            nodo.agregarHijo(new Nodo(TipoNodo.Operador, tokens.get(indice).valor));
                            indice++;
                            nodo.agregarHijo(termino());
                        }
                        return nodo;
                    }
            
                    private Nodo termino() {
                        Nodo nodo = new Nodo(TipoNodo.Termino, "Término");
                        nodo.agregarHijo(factor());
                        while (indice < tokens.size() && (tokens.get(indice).tipo == TipoToken.OPERADOR && (tokens.get(indice).valor.equals("*") || tokens.get(indice).valor.equals("/")))) {
                            nodo.agregarHijo(new Nodo(TipoNodo.Operador, tokens.get(indice).valor));
                            indice++;
                            nodo.agregarHijo(factor());
                        }
                        return nodo;
                    }
            
                    private Nodo factor() {
                        Nodo nodo;
                        if (tokens.get(indice).tipo == TipoToken.ID) {
                            nodo = new Nodo(TipoNodo.Identificador, tokens.get(indice).valor);
                            indice++;
                        } else if (tokens.get(indice).tipo == TipoToken.Constante) {
                            nodo = new Nodo(TipoNodo.Constante, tokens.get(indice).valor);
                            indice++;
                        } else if (tokens.get(indice).tipo == TipoToken.OPERADOR && tokens.get(indice).valor.equals("(")) {
                            indice++; // Consumir el parentesis de apertura
                            nodo = expresion();
                            consumirToken(TipoToken.OPERADOR, ")"); // Consumir el parentesis de cierre
                        } else {
                        // Manejar error sintáctico: factor no reconocido
                        //System.out.println("Error sintáctico: factor no reconocido");
                        return null;
                        }
                        return nodo;
                    }
            
                    private void consumirToken(TipoToken tipo, String valor) {
                        if (indice < tokens.size() && tokens.get(indice).tipo == tipo && tokens.get(indice).valor.equals(valor)) {
                            indice++;
                        } else {
                            // Manejar error sintáctico: token esperado no encontrado
                        }
                    }
    }

    //eliminacion de los comanetarios
    private static String eliminarComentarios(String linea)
    {
        int inicioComentario = linea.indexOf("//"); //busca la posicion del inicio del comentario
        if (inicioComentario != -1) {
            linea = linea.substring(0, inicioComentario); //encontrado el comentario, se actualiza la variable linea
            //para comentar soo texto antes del //
        }
        return linea; //ya devuelve la linea sin comentario los elimina
    }

    //devuelve la lista de tokens
    private static List<Token> tokenizar(String linea) {
        List<Token> tokens = new ArrayList<>();
        int indice = 0;
        while (indice < linea.length()) /*aca da el inicio para recorrer 
        la liena de entrada caracter por caracter*/
        {
            char c = linea.charAt(indice); //posicion actual del inidice
            if (Character.isWhitespace(c))  /*verifica si el caracter es un espacio en blanco
            y lo ifnora*/
            {
                indice++;
                continue;
            }
            //para las cadenas
            if (c == '"') {
                StringBuilder cadena = new StringBuilder(); //para construir la cadena entre comillas
                cadena.append(c);
                indice++;
                while (indice < linea.length() && linea.charAt(indice) != '"')
                //bucle para contruir la cadena hasta su cierre de comillas
                {
                    cadena.append(linea.charAt(indice));
                    indice++;
                }
                if (indice < linea.length() && linea.charAt(indice) == '"')
                 /*verifica si se encontro el cierre de comillas y lo agrega al token cadena*/
                {
                    cadena.append('"');
                    indice++;
                    tokens.add(new Token(TipoToken.Cadena, cadena.toString()));
                } else {
                    tokens.add(new Token(TipoToken.ERROR, cadena.toString()));
                }
                continue;
            }
            if (c == '(' || c == ')' || c == '{' || c == '}' || c == ';') {
                tokens.add(new Token(TipoToken.OPERADOR, String.valueOf(c)));
                indice++;
                continue;
            }
            StringBuilder buffer = new StringBuilder(); //para construir otros tipos de tokens
            buffer.append(c);
            indice++; //avanza al sig caracter
            boolean encontradoSiguienteToken = false; /*controla si se ha encontrado el fianl del 
            token actual*/
            while (indice < linea.length() && !encontradoSiguienteToken) /*tokens que no son cadenas 
            ni operadores*/
            {
                char siguiente = linea.charAt(indice);
                if (Character.isWhitespace(siguiente) || poperador.matcher(String.valueOf(siguiente)).matches() || siguiente == '"' || siguiente == '(' || siguiente == ')' || siguiente == '{' || siguiente == '}' || siguiente == ';') {
                    encontradoSiguienteToken = true;
                } else {
                    buffer.append(siguiente);
                    indice++;
                }
            }
            String valor = buffer.toString();
            if (palabrasReservadas.contains(valor)) {
                tokens.add(new Token(TipoToken.PalabraReservada, valor)); /*se agrega a la lista de tokens*/
            } else if (combinacionesPosibles.contains(valor)) {
                tokens.add(new Token(TipoToken.COMBINACION, valor));
            } else if (pconstante.matcher(valor).matches()) {
                tokens.add(new Token(TipoToken.Constante, valor));
            } else if (poperador.matcher(valor).matches()) {
                tokens.add(new Token(TipoToken.OPERADOR, valor));
            } else if (identi.matcher(valor).matches()) {
                tokens.add(new Token(TipoToken.ID, valor));
            } else {
                tokens.add(new Token(TipoToken.ERROR, valor));
            }
        }
        return tokens;
    }

    private static List<ErrorLexico> filtrarErrores(List<Token> tokens) 
    {
        List<ErrorLexico> errores = new ArrayList<>(); //se van a almacenar los errores lexicos
        for (Token token : tokens) {
            if (token.tipo == TipoToken.ERROR) {
                errores.add(new ErrorLexico(token.valor, TipoError.VALOR_INVALIDO));
                /*agrega un nuevo objeto a la lista de errores, utilizando el valor del token como descripción 
                del error y asignando un tipo de error VALOR_INVALIDO.*/
            }
        }
        return errores;
    }

    //enumerado de tokens
    private enum TipoToken {
        PalabraReservada, COMBINACION, Constante, OPERADOR, ID, Cadena, ERROR
    }

    //enumeracion de los erroes
    private enum TipoError {
        VALOR_INVALIDO
    }

    //clase token del analizador
    private static class Token 
    {
        private final TipoToken tipo; //declaracion de campo
        private final String valor;

        public Token(TipoToken tipo, String valor) {
            this.tipo = tipo;
            this.valor = valor;
        }

        //por si hay una cadena
        @Override
        public String toString() {
            return tipo + ": " + valor;
        }
    }

    //clase de los errores
    private static class ErrorLexico {
        private final String valor; //va almacenar la descripcion del error
        private final TipoError tipo; //representa ell tipo de error lexico

        public ErrorLexico(String valor, TipoError tipo) {
            this.valor = valor;
            this.tipo = tipo;
        }

        @Override
        public String toString() {
            return "Error: " + valor + " (" + tipo + ")";
        }
    }
}