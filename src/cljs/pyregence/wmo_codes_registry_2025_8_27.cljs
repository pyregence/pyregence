(ns pyregence.wmo-codes-registry-2025-8-27)

(def wmo-unit-id->labels
  "A mapping of wmo-id's to labels curated from https://codes.wmo.int/common/unit in 2025"
  {"(Z)_pref" {"skos:altLabel" "(Z)", "skos:prefLabel" "(zetta)"},
   "Bq" {"skos:altLabel" "Bq", "skos:prefLabel" "becquerel"},
   "T" {"skos:altLabel" "T", "skos:prefLabel" "tesla"},
   "nautical_mile" {"skos:altLabel" " ", "skos:prefLabel" "nautical mile"},
   "percent" {"skos:altLabel" "%", "skos:prefLabel" "per cent"},
   "hPa" {"skos:altLabel" "hPa", "skos:prefLabel" "hectopascal"},
   "d" {"skos:altLabel" "d", "skos:prefLabel" "day"},
   "K_m2_kg-1_s-1"
   {"skos:altLabel" "K m^2 kg^-1 s^-1",
    "skos:prefLabel" "kelvin square metres per kilogram per second"},
   "rad" {"skos:altLabel" "rad", "skos:prefLabel" "radian"},
   "K" {"skos:altLabel" "K", "skos:prefLabel" "kelvin"},
   "cb_s-1" {"skos:altLabel" "cb s^-1", "skos:prefLabel" "centibars per second"},
   "P_pref" {"skos:altLabel" "P", "skos:prefLabel" "peta"},
   "'" {"skos:altLabel" "'", "skos:prefLabel" "minute (angle)"},
   "deg_s-1" {"skos:altLabel" "degree/s", "skos:prefLabel" "degrees per second"},
   "da_pref" {"skos:altLabel" "da", "skos:prefLabel" "deca"},
   "AU" {"skos:altLabel" "AU", "skos:prefLabel" "astronomic unit"},
   "kt_km-1"
   {"skos:altLabel" "kt/1000 m", "skos:prefLabel" "knots per 1000 metres"},
   "m3_s-1"
   {"skos:altLabel" "m^3 s^-1", "skos:prefLabel" "cubic metres per second"},
   "min" {"skos:altLabel" "min", "skos:prefLabel" "minute (time)"},
   "kg-2_s-1"
   {"skos:altLabel" "kg^-2 s^-1",
    "skos:prefLabel" "per square kilogram per second"},
   "s" {"skos:altLabel" "s", "skos:prefLabel" "second"},
   "J_kg-1" {"skos:altLabel" "J kg^-1", "skos:prefLabel" "joules per kilogram"},
   "rad_m-1" {"skos:altLabel" "rad m^-1", "skos:prefLabel" "radians per metre"},
   "hPa_h-1"
   {"skos:altLabel" "hPa h^-1", "skos:prefLabel" "hectopascals per hour"},
   "h_pref" {"skos:altLabel" "h", "skos:prefLabel" "hecto"},
   "kg_kg-1"
   {"skos:altLabel" "kg kg^-1", "skos:prefLabel" "kilograms per kilogram"},
   "W_m-1_sr-1"
   {"skos:altLabel" "W m^-1 sr^-1",
    "skos:prefLabel" "watts per metre per steradian"},
   "Gy" {"skos:altLabel" "Gy", "skos:prefLabel" "gray"},
   "degrees_true" {"skos:altLabel" "˚", "skos:prefLabel" "degrees true"},
   "mm_s-1"
   {"skos:altLabel" "mm s^-1", "skos:prefLabel" "millimetres per seconds"},
   "mon" {"skos:altLabel" "mon", "skos:prefLabel" "month"},
   "dPa_s-1"
   {"skos:altLabel" "dPa s^-1",
    "skos:prefLabel" "decipascals per second (microbar per second)"},
   "Pa" {"skos:altLabel" "Pa", "skos:prefLabel" "pascal"},
   "m3" {"skos:altLabel" "m^3", "skos:prefLabel" "cubic metres"},
   "W_m-3_sr-1"
   {"skos:altLabel" "W m^-3 sr^-1",
    "skos:prefLabel" "watts per cubic metre per steradian"},
   "sr" {"skos:altLabel" "sr", "skos:prefLabel" "steradian"},
   "eV" {"skos:altLabel" "eV", "skos:prefLabel" "electron volt"},
   "m_s-2"
   {"skos:altLabel" "m s^-2", "skos:prefLabel" "metres per second squared"},
   "E_pref" {"skos:altLabel" "E", "skos:prefLabel" "exa"},
   "W_m-2_sr-1_m"
   {"skos:altLabel" "W m^-2 sr^-1 m",
    "skos:prefLabel" "watts per square metre per steradian metre"},
   "N_m-2"
   {"skos:altLabel" "N m^-2", "skos:prefLabel" "newtons per square metre"},
   "km_h-1" {"skos:altLabel" "km h^-1", "skos:prefLabel" "kilometres per hour"},
   "m4" {"skos:altLabel" "m^4", "skos:prefLabel" "metres to the fourth power"},
   "Wb" {"skos:altLabel" "Wb", "skos:prefLabel" "weber"},
   "log_(m-1)"
   {"skos:altLabel" "log (m^-1)", "skos:prefLabel" "logarithm per metre"},
   "n_pref" {"skos:altLabel" "n", "skos:prefLabel" "nano"},
   "gpm" {"skos:altLabel" "gpm", "skos:prefLabel" "geopotential metre"},
   "K_m_s-1"
   {"skos:altLabel" "K m s^-1", "skos:prefLabel" "kelvin metres per second"},
   "hPa_-1"
   {"skos:altLabel" "hPa/3 h", "skos:prefLabel" "hectopascals per 3 hours"},
   "cm_s-1"
   {"skos:altLabel" "cm s^-1", "skos:prefLabel" "centimetres per second"},
   "lm" {"skos:altLabel" "lm", "skos:prefLabel" "lumen"},
   "p_pref" {"skos:altLabel" "p", "skos:prefLabel" "pico"},
   "T_pref" {"skos:altLabel" "T", "skos:prefLabel" "tera"},
   "Bq_l-1" {"skos:altLabel" "Bq l^-1", "skos:prefLabel" "becquerels per litre"},
   "Hz" {"skos:altLabel" "Hz", "skos:prefLabel" "hertz"},
   "C_-1"
   {"skos:altLabel" "˚ C/100 m",
    "skos:prefLabel" "degrees Celsius per 100 metres"},
   "dm" {"skos:altLabel" "dm", "skos:prefLabel" "decimetre"},
   "m-1" {"skos:altLabel" "m^-1", "skos:prefLabel" "per metre"},
   "daPa" {"skos:altLabel" "daPa", "skos:prefLabel" "dekapascal"},
   "J" {"skos:altLabel" "J", "skos:prefLabel" "joule"},
   "N_units" {"skos:altLabel" "N units", "skos:prefLabel" "N units"},
   "d_pref" {"skos:altLabel" "d", "skos:prefLabel" "deci"},
   "mm" {"skos:altLabel" "mm", "skos:prefLabel" "millimetre"},
   "m2_Hz-1"
   {"skos:altLabel" "m^2 Hz^-1", "skos:prefLabel" "square metres per hertz"},
   "c_pref" {"skos:altLabel" "c", "skos:prefLabel" "centi"},
   "Sv" {"skos:altLabel" "Sv", "skos:prefLabel" "sievert"},
   "S" {"skos:altLabel" "S", "skos:prefLabel" "siemens"},
   "0.001" {"skos:altLabel" "‰", "skos:prefLabel" "parts per thousand"},
   "m2_rad-1_s"
   {"skos:altLabel" "m^2 rad^-1 s",
    "skos:prefLabel" "square metres per radian squared"},
   "mm6_m-3"
   {"skos:altLabel" "mm^6 m^-3",
    "skos:prefLabel" "millimetres per the sixth power per cubic metre"},
   "dB" {"skos:altLabel" "dB", "skos:prefLabel" "decibel (6)"},
   "H" {"skos:altLabel" "H", "skos:prefLabel" "henry"},
   "kg_m-1" {"skos:altLabel" "km m^-1", "skos:prefLabel" "kilograms per metre"},
   "dB_m-1" {"skos:altLabel" "dB m^-1", "skos:prefLabel" "decibels per metre"},
   "s-2" {"skos:altLabel" "s^-2", "skos:prefLabel" "per second squared"},
   "okta" {"skos:altLabel" "okta", "skos:prefLabel" "eighths of cloud"},
   "s-1" {"skos:altLabel" "s^-1", "skos:prefLabel" "per second (same as hertz)"},
   "C" {"skos:altLabel" "C", "skos:prefLabel" "coulomb"},
   "W_m-2_sr-1"
   {"skos:altLabel" "W m^-2 sr^-1",
    "skos:prefLabel" "watts per square metre per steradian"},
   "cd" {"skos:altLabel" "cd", "skos:prefLabel" "candela"},
   "F" {"skos:altLabel" "F", "skos:prefLabel" "farad"},
   "m2_s-1"
   {"skos:altLabel" "m^2 s^-1", "skos:prefLabel" "square metres per second"},
   "Bq_s_m-3"
   {"skos:altLabel" "Bq s m^-3",
    "skos:prefLabel" "becquerel seconds per cubic metre"},
   "kg_kg-1_s-1"
   {"skos:altLabel" "kg kg^-1 s^-1",
    "skos:prefLabel" "kilograms per kilogram per second"},
   "u_pref" {"skos:altLabel" "µ", "skos:prefLabel" "micro"},
   "a" {"skos:altLabel" "a", "skos:prefLabel" "year"},
   "dB_deg-1"
   {"skos:altLabel" "dB degree^-1", "skos:prefLabel" "decibels per degree"},
   "cm_h-1" {"skos:altLabel" "cm h^-1", "skos:prefLabel" "centimetres per hour"},
   "hPa_s-1"
   {"skos:altLabel" "hPa s^-1", "skos:prefLabel" "hectopascals per second"},
   "week" {"skos:altLabel" " ", "skos:prefLabel" "week"},
   "ha" {"skos:altLabel" "ha", "skos:prefLabel" "hectare"},
   "degC" {"skos:altLabel" "˚ C", "skos:prefLabel" "degrees Celsius (8)"},
   "deg2" {"skos:altLabel" "degrees^2", "skos:prefLabel" "square degrees"},
   "degree_(angle)" {"skos:altLabel" "˚", "skos:prefLabel" "degree (angle)"},
   "pH_unit" {"skos:altLabel" "pH unit", "skos:prefLabel" "pH unit"},
   "(y)_pref" {"skos:altLabel" "(y)", "skos:prefLabel" "(yocto)"},
   "mol_mol-1"
   {"skos:altLabel" " mol mol^-1", "skos:prefLabel" "moles per mole"},
   "k_pref" {"skos:altLabel" "k", "skos:prefLabel" "kilo"},
   "t" {"skos:altLabel" "t", "skos:prefLabel" "tonne"},
   "a_pref" {"skos:altLabel" "a", "skos:prefLabel" "atto"},
   "m_s-1" {"skos:altLabel" "m s^-1", "skos:prefLabel" "metres per second"},
   "V" {"skos:altLabel" "V", "skos:prefLabel" "volt"},
   "Ohm" {"skos:altLabel" "Ω", "skos:prefLabel" "ohm"},
   "m3_m-3"
   {"skos:altLabel" "m^3 m^-3", "skos:prefLabel" "cubic metres per cubic metre"},
   "m_s-1_km-1"
   {"skos:altLabel" "m s^-1/1000 m",
    "skos:prefLabel" "metres per second per 1000 metres"},
   "W_m-2" {"skos:altLabel" "W m^-2", "skos:prefLabel" "watts per square metre"},
   "kPa" {"skos:altLabel" "kPa", "skos:prefLabel" "kilopascal"},
   "K_m-1" {"skos:altLabel" "K m^-1", "skos:prefLabel" "kelvins per metre"},
   "km" {"skos:altLabel" "km", "skos:prefLabel" "kilometre"},
   "m2_s" {"skos:altLabel" "m^2 s", "skos:prefLabel" "square metres second"},
   "kg_m-2"
   {"skos:altLabel" "kg m^-2", "skos:prefLabel" "kilograms per square metre"},
   "Cel" {"skos:altLabel" "˚C", "skos:prefLabel" "degree Celsius"},
   "1" {"skos:altLabel" "1", "skos:prefLabel" "Dimensionless"},
   "Pa_s-1" {"skos:altLabel" "Pa s^-1", "skos:prefLabel" "pascals per second"},
   "(Y)_pref" {"skos:altLabel" "(Y)", "skos:prefLabel" "(yotta)"},
   "Bq_m-2"
   {"skos:altLabel" "Bq m^-2", "skos:prefLabel" "becquerels per square metre"},
   "kg" {"skos:altLabel" "kg", "skos:prefLabel" "kilogram"},
   "m2" {"skos:altLabel" "m^2", "skos:prefLabel" "square metres"},
   "g" {"skos:altLabel" "g", "skos:prefLabel" "acceleration due to gravity"},
   "l" {"skos:altLabel" "l", "skos:prefLabel" "litre"},
   "N" {"skos:altLabel" "N", "skos:prefLabel" "newton"},
   "g_kg-1_s-1"
   {"skos:altLabel" "g kg^-1 s^-1",
    "skos:prefLabel" "grams per kilogram per second"},
   "unit" {},
   "u" {"skos:altLabel" "u", "skos:prefLabel" "atomic mass unit"},
   "lx" {"skos:altLabel" "lx", "skos:prefLabel" "lux"},
   "m_pref" {"skos:altLabel" "m", "skos:prefLabel" "milli"},
   "nbar" {"skos:altLabel" "nbar", "skos:prefLabel" "nanobar = hPa 10^-6"},
   "f_pref" {"skos:altLabel" "f", "skos:prefLabel" "femto"},
   "A" {"skos:altLabel" "A", "skos:prefLabel" "ampere"},
   "G_pref" {"skos:altLabel" "G", "skos:prefLabel" "giga"},
   "kt" {"skos:altLabel" "kt", "skos:prefLabel" "knot"},
   "s_m-1" {"skos:altLabel" "s m^-1", "skos:prefLabel" "seconds per metre"},
   "h" {"skos:altLabel" "h", "skos:prefLabel" "hour"},
   "''" {"skos:altLabel" "''", "skos:prefLabel" "second (angle)"},
   "km_d-1" {"skos:altLabel" "km/d", "skos:prefLabel" "kilometres per day"},
   "M_pref" {"skos:altLabel" "M", "skos:prefLabel" "mega"},
   "Bq_m-3"
   {"skos:altLabel" "Bq m^-3", "skos:prefLabel" "becquerels per cubic metre"},
   "m2_s-2"
   {"skos:altLabel" "m^2 s^-2",
    "skos:prefLabel" "square metres per second squared"},
   "g_kg-1" {"skos:altLabel" "g kg^-1", "skos:prefLabel" "grams per kilogram"},
   "DU" {"skos:altLabel" "DU", "skos:prefLabel" "Dobson Unit (9)"},
   "kg_m-3"
   {"skos:altLabel" "kg m^-3", "skos:prefLabel" "kilograms per cubic metre"},
   "(z)_pref" {"skos:altLabel" "(z)", "skos:prefLabel" "(zepto)"},
   "m" {"skos:altLabel" "m", "skos:prefLabel" "metre"},
   "pc" {"skos:altLabel" "pc", "skos:prefLabel" "parsec"},
   "C_m-1"
   {"skos:altLabel" "˚ C/m", "skos:prefLabel" "degrees Celsius per metre"},
   "W" {"skos:altLabel" "W", "skos:prefLabel" "watt"},
   "mSv" {"skos:altLabel" "mSv", "skos:prefLabel" "millisievert"},
   "m_s-1_m-1"
   {"skos:altLabel" "m s^-1/m", "skos:prefLabel" "metres per second per metre"},
   "W_m-2_sr-1_cm"
   {"skos:altLabel" "W m^-2 sr^-1 cm",
    "skos:prefLabel" "watts per square metre per steradian centimetre"},
   "mol" {"skos:altLabel" "mol", "skos:prefLabel" "mole"},
   "m2_-1"
   {"skos:altLabel" "m^2/3 s^-1",
    "skos:prefLabel" "metres to the two thirds power per second"},
   "kg_m-2_s-1"
   {"skos:altLabel" "kg m^-2 s^-1",
    "skos:prefLabel" "kilograms per square metre per second"},
   "cm" {"skos:altLabel" "cm", "skos:prefLabel" "centimetre"},
   "mm_h-1" {"skos:altLabel" "mm h^-1", "skos:prefLabel" "millimetres per hour"},
   "J_m-2"
   {"skos:altLabel" "J m^-2", "skos:prefLabel" "joules per square metre"},
   "cb_-1"
   {"skos:altLabel" "cb/12 h", "skos:prefLabel" "centibars per 12 hours"},
   "log_(m-2)"
   {"skos:altLabel" "log (m^-2)", "skos:prefLabel" "logarithm per square metre"},
   "S_m-1" {"skos:altLabel" "S m^-1", "skos:prefLabel" "siemens per metre"},
   "ft" {"skos:altLabel" "ft", "skos:prefLabel" "foot"}})
