import csv
import matplotlib.pyplot as plt

# Variable globale pour choisir le type de scalabilité (True = Forte, False = Faible)
SCALABILITE_FORTE = True  # Mettre à False pour tester la scalabilité faible


# Fonction pour calculer le speedup
def calculate_speedup(data):
    speedups = []
    for i in range(len(data)):
        speedup = data[0] / data[i]
        speedups.append(speedup)
    return speedups


# Charger les données depuis le fichier CSV
filename = "../resultats.csv"
data = {}

# Lire les données du fichier CSV
with open(filename, newline='', encoding='utf-8') as csvfile:
    reader = csv.DictReader(csvfile)
    for row in reader:
        impl = row['Implémentation']
        if impl not in data:
            data[impl] = {}
        points_lances = int(row['Points lancés'])
        coeurs = int(row['Nombre de coeurs'])
        temps_execution = float(row['Temps d\'exécution (ms)'])

        sort_value = points_lances if SCALABILITE_FORTE else (points_lances // coeurs)

        if sort_value not in data[impl]:
            data[impl][sort_value] = {'coeurs': [], 'temps': []}

        data[impl][sort_value]['coeurs'].append(coeurs)
        data[impl][sort_value]['temps'].append(temps_execution)

# Tracer les graphiques pour chaque total de points lancés
for impl in data:
    for points_lances, values in data[impl].items():
        coeurs = values['coeurs']
        temps = values['temps']

        speedups = calculate_speedup(temps)
        if SCALABILITE_FORTE:
            ideal_speedup = coeurs  # Speedup idéal pour la scalabilité forte
        else:
            ideal_speedup = [1] * len(coeurs)  # Speedup idéal pour la scalabilité faible

        plt.figure(figsize=(6, 6))
        per_worker = "" if SCALABILITE_FORTE else " par worker"
        plt.plot(coeurs, speedups, marker='o', label=f"{points_lances} points{per_worker}")
        plt.plot(coeurs, ideal_speedup, linestyle='--', color='r', label="Speedup idéal")

        plt.xlabel("Nombre de cœurs")
        plt.ylabel("Speedup")
        plt.title(f"Speedup pour {points_lances} points{per_worker} sur {impl}")
        plt.grid(True)
        plt.legend()

        plt.tight_layout()
        scalabilite = "forte" if SCALABILITE_FORTE else "faible"
        plt.savefig(f"scalabilite_{scalabilite}_{impl}_{points_lances}.png")
        plt.show()
