# -*- coding: utf-8 -*-
import re
import sys
import pandas as pd
from datetime import datetime
from pypdf import PdfReader

def extraer_texto_crudo(ruta_pdf):
    texto_completo = []
    try:
        reader = PdfReader(ruta_pdf)
        for pagina in reader.pages:
            texto = pagina.extract_text()
            if texto:
                texto_completo.append(texto)
        return "\n".join(texto_completo)
    except Exception as e:
        print(f"Error al abrir o leer el PDF: {e}")
        return ""

def procesar_extracto_pdf(ruta_pdf, ano_ejecucion=2026, filtrar=True):
    texto = extraer_texto_crudo(ruta_pdf)
    if not texto:
        return

    # Expresión regular robusta para el formato extraído por pypdf:
    # 1. Comprobante, Día, Mes, Año (10 dígitos compactados)
    # 2. Tasa de interés/E.A. (ej. 25.45 o 0.00)
    # 3. Valor Compra (opcionalmente con $)
    # 4. Valor Cuota (opcionalmente con $)
    # 5. Saldo Pendiente (opcionalmente con $)
    # 6. Cuotas Totales, Facturadas, Pendientes (6 dígitos: 2+2+2)
    # 7. Detalle de comercio al final de la línea
    patron_universal = re.compile(
        r'^(\d{4})(\d{2})(\d{2})(\d{2})\s+'           # Comprobante(4), Dia(2), Mes(2), Año(2)
        r'(\d{1,2}\.\d{2})'                            # Tasa de interés (ej. 25.45 o 0.00)
        r'\$?(-?[\d,]+\.\d{2})'                        # Valor Compra
        r'\$?(-?[\d,]+\.\d{2})'                        # Valor Cuota
        r'\$?(-?[\d,]+\.\d{2})'                        # Saldo Pendiente
        r'(\d{2})(\d{2})(\d{2})'                       # Cuotas Totales, Facturadas, Pendientes
        r'\s*(.+)$'                                    # Detalle
    )

    compras_detectadas = []
    lineas = texto.split('\n')

    for linea in lineas:
        # Limpieza básica para estandarizar la lectura de la línea del PDF
        linea_limpia = linea.replace('"', '').replace("'", "").strip()
        match = patron_universal.search(linea_limpia)

        if match:
            comprobante = match.group(1)
            dia = int(match.group(2))
            mes = int(match.group(3))
            ano_transaccion = 2000 + int(match.group(4))
            
            # Reemplazar múltiples espacios en el comercio por un espacio único
            detalle = re.sub(r'\s+', ' ', match.group(12).strip())

            # Filtros de seguridad para ignorar transacciones administrativas o abonos
            if filtrar:
                if any(palabra in detalle.upper() for palabra in ["PAGO MES", "REINTEGRO", "SEGURO DE VIDA", "CUOTA DE MANEJO", "INTERESES FACTURADOS"]):
                    continue

            try:
                valor_total = float(match.group(6).replace(',', ''))
            except ValueError:
                continue

            try:
                valor_cuota_parsed = float(match.group(7).replace(',', ''))
            except ValueError:
                valor_cuota_parsed = 0.0

            if valor_total == 0.0:
                valor_total = valor_cuota_parsed

            cuotas_totales = int(match.group(9))
            if cuotas_totales == 0:
                cuotas_totales = 1

            # --- LÓGICA DE NEGOCIO (CORTE EL 26, PAGO EL 16 DEL SIGUIENTE MES) ---
            f_compra = datetime(ano_transaccion, mes, dia)
            mes_fact = f_compra.month if f_compra.day <= 26 else f_compra.month + 1
            ano_fact = f_compra.year
            if mes_fact > 12:
                mes_fact = 1
                ano_fact += 1

            cronograma = {}
            # Si el total fue derivado del valor de cuota del extracto, proyectamos usando ese valor directamente
            if 'valor_cuota_parsed' in locals() and valor_total == valor_cuota_parsed:
                valor_cuota = valor_cuota_parsed
            else:
                valor_cuota = valor_total / cuotas_totales

            for i in range(1, cuotas_totales + 1):
                mes_pago = mes_fact + i
                ano_pago = ano_fact + (mes_pago - 1) // 12
                mes_pago = (mes_pago - 1) % 12 + 1
                cronograma[f"Pago_Cuota_{i}"] = f"16/{mes_pago:02d}/{ano_pago} (${valor_cuota:,.2f})"

            item = {
                "Comprobante": comprobante,
                "Fecha Compra": f"{dia:02d}/{mes:02d}/{ano_transaccion}",
                "Detalle": detalle,
                "Valor Total": valor_total,
                "Cuotas Totales": cuotas_totales
            }
            item.update(cronograma)
            compras_detectadas.append(item)

    if compras_detectadas:
        df = pd.DataFrame(compras_detectadas)
        # Eliminar registros duplicados inter-mensuales si se procesan varios PDFs juntos
        df = df.drop_duplicates(subset=["Comprobante", "Fecha Compra"], keep="first")
        
        # Obtener la fecha del último movimiento y la fecha de corte
        fechas_datetime = []
        fechas_fact = []
        for x in compras_detectadas:
            f = datetime.strptime(x["Fecha Compra"], "%d/%m/%Y")
            fechas_datetime.append(f)
            
            mes_f = f.month if f.day <= 26 else f.month + 1
            ano_f = f.year
            if mes_f > 12:
                mes_f = 1
                ano_f += 1
            fechas_fact.append(datetime(ano_f, mes_f, 26))

        fecha_ultimo_movimiento = max(fechas_datetime).strftime("%d/%m/%Y")
        fecha_corte = max(fechas_fact).strftime("%d/%m/%Y")

        archivo_salida = "cronograma_pagos_universal.csv"
        df.to_csv(archivo_salida, index=False, encoding="utf-8-sig")
        print(f"\n[ÉXITO] Se procesó el archivo PDF correctamente.")
        print(f"-> Se detectaron {len(df)} compras reales.")
        print(f"-> Último movimiento registrado: {fecha_ultimo_movimiento}")
        print(f"-> Fecha de corte del extracto: {fecha_corte}")
        print(f"-> Archivo generado: '{archivo_salida}'")
    else:
        print("\n[ERROR] No se encontraron transacciones con el formato del banco en este PDF.")

if __name__ == "__main__":
    # Buscar si se especificó el parámetro --todo o --filtrar
    args_limpios = [a for a in sys.argv if a not in ("--todo", "--filtrar")]
    
    if len(args_limpios) > 1:
        ruta = args_limpios[1]
        
        if "--todo" in sys.argv:
            filtrar = False
        elif "--filtrar" in sys.argv:
            filtrar = True
        else:
            try:
                opcion = input("¿Desea filtrar cobros administrativos y abonos (Seguro de vida, cuota de manejo, pagos, reintegros)? [S/n]: ").strip().lower()
                if opcion == 'n':
                    filtrar = False
                else:
                    filtrar = True
            except (KeyboardInterrupt, EOFError):
                print("\nUsando filtro por defecto (S).")
                filtrar = True
                
        procesar_extracto_pdf(ruta, filtrar=filtrar)
    else:
        print("Uso: python extractor.py <nombre_del_archivo.pdf> [--todo]")