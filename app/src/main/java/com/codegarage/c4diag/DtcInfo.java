package com.codegarage.c4diag;

import java.util.HashMap;
import java.util.Map;

/** Descripciones en español de códigos genéricos (SAE) habituales en diésel. */
public final class DtcInfo {
    private static final Map<String, String> M = new HashMap<>();
    static {
        M.put("P0087", "Presión del raíl de combustible demasiado baja");
        M.put("P0088", "Presión del raíl de combustible demasiado alta");
        M.put("P0093", "Fuga de combustible detectada");
        M.put("P0100", "Caudalímetro (MAF): fallo de circuito");
        M.put("P0101", "Caudalímetro (MAF): valor fuera de rango");
        M.put("P0102", "Caudalímetro (MAF): señal baja");
        M.put("P0103", "Caudalímetro (MAF): señal alta");
        M.put("P0105", "Sensor de presión de admisión (MAP): fallo de circuito");
        M.put("P0106", "Sensor de presión de admisión (MAP): fuera de rango");
        M.put("P0110", "Sensor de temperatura del aire de admisión: fallo de circuito");
        M.put("P0115", "Sensor de temperatura del refrigerante: fallo de circuito");
        M.put("P0190", "Sensor de presión del raíl: fallo de circuito");
        M.put("P0191", "Sensor de presión del raíl: fuera de rango");
        M.put("P0200", "Inyectores: fallo de circuito");
        M.put("P0201", "Inyector cilindro 1: fallo de circuito");
        M.put("P0202", "Inyector cilindro 2: fallo de circuito");
        M.put("P0203", "Inyector cilindro 3: fallo de circuito");
        M.put("P0204", "Inyector cilindro 4: fallo de circuito");
        M.put("P0234", "Sobrepresión del turbo");
        M.put("P0299", "Presión del turbo insuficiente");
        M.put("P0335", "Sensor de posición del cigüeñal: fallo de circuito");
        M.put("P0340", "Sensor del árbol de levas: fallo de circuito");
        M.put("P0380", "Calentadores (bujías de precalentamiento): fallo de circuito");
        M.put("P0400", "EGR: fallo de caudal");
        M.put("P0401", "EGR: caudal insuficiente");
        M.put("P0403", "EGR: fallo de circuito");
        M.put("P0500", "Señal de velocidad del vehículo (en Citroën la da el ABS)");
        M.put("P0501", "Señal de velocidad del vehículo fuera de rango");
        M.put("P0560", "Tensión del sistema (batería) incorrecta");
        M.put("P0562", "Tensión del sistema baja");
        M.put("P0563", "Tensión del sistema alta");
        M.put("P0571", "Contacto del pedal de freno: fallo de circuito");
        M.put("P0600", "Fallo de comunicación de la centralita (bus)");
        M.put("P0606", "Fallo interno del procesador de la centralita");
        M.put("P0670", "Módulo de calentadores: fallo de circuito");
        M.put("P0704", "Contacto del pedal de embrague: fallo de circuito");
        M.put("P2002", "Filtro de partículas: eficiencia por debajo del umbral");
        M.put("P2263", "Sistema del turbo: rendimiento incorrecto");
        M.put("P242F", "Filtro de partículas (FAP) obstruido por acumulación de ceniza");
        M.put("P2452", "Sensor de presión diferencial del FAP: fallo de circuito");
        M.put("P2453", "Sensor de presión diferencial del FAP: fuera de rango");
        M.put("P2458", "Regeneración del FAP: duración excesiva");
        M.put("P2463", "Filtro de partículas (FAP) obstruido por acumulación de hollín");
        M.put("U0001", "Bus CAN de alta velocidad: fallo de comunicación");
        M.put("U0100", "Pérdida de comunicación con la centralita del motor");
        M.put("U0121", "Pérdida de comunicación con el ABS");
    }
    private DtcInfo() { }

    public static String describe(String code) {
        String d = M.get(code);
        if (d != null) return d;
        switch (code.charAt(0)) {
            case 'P': return "Motor / transmisión (toca para buscar el significado)";
            case 'C': return "Chasis: ABS / ESP / frenos (toca para buscar)";
            case 'B': return "Carrocería (toca para buscar)";
            default:  return "Red / comunicación entre centralitas (toca para buscar)";
        }
    }
}
