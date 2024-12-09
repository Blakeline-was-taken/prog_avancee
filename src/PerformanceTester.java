import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class PerformanceTester {
    public static void main(String[] args) throws IOException {
        String filename = "resultats.csv";
        FileWriter writer = new FileWriter(filename);

        // Écrire l'en-tête dans le fichier CSV
        writer.write("Implémentation,Nombre de coeurs,Points lancés,Points par coeur,Temps d'exécution (ms),Approximation de PI,Erreur,Nombre de tests\n");

        // Liste des implémentations à tester
        MonteCarloImplementation[] implementations = {
                new PiJavaImplementation(), // Implémentation Pi.java
                new Assignment102Implementation() // Implémentation Assignment102
        };

        for (MonteCarloImplementation impl : implementations) {
            // Charger les données de test
            String testDataFile = "test_scalab_faible.csv";
            BufferedReader reader = new BufferedReader(new FileReader(testDataFile));
            String line;

            // Sauter l'en-tête
            reader.readLine();

            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                int cores = Integer.parseInt(parts[0]);
                int totalPoints = Integer.parseInt(parts[1]);
                int numTests = Integer.parseInt(parts[2]);

                long totalTime = 0;
                double totalPi = 0;

                for (int test = 0; test < numTests; test++) {
                    long startTime = System.currentTimeMillis();
                    System.out.println("| TEST : " + test + " | Impl : " + impl.getName() + " | Points : " + totalPoints + " | Cores : " + cores + " |");
                    double pi = impl.execute(totalPoints, cores);
                    long endTime = System.currentTimeMillis();

                    totalPi += pi;
                    totalTime += (endTime - startTime);
                }

                // Calculer les moyennes
                double avgTime = (double) totalTime / numTests;
                double avgPi = totalPi / numTests;
                double error = Math.abs((avgPi - Math.PI) / Math.PI);

                // Écrire les résultats dans le fichier
                writer.write(impl.getName() + "," + cores + "," + totalPoints + "," + (totalPoints / cores) + "," + avgTime + "," + avgPi + "," + error + "," + numTests + "\n");
            }
            reader.close();
        }

        writer.close();
        System.out.println("Résultats enregistrés dans " + filename);
    }
}

// Interface pour toutes les implémentations Monte-Carlo
interface MonteCarloImplementation {
    String getName(); // Nom de l'implémentation
    double execute(int totalPoints, int numCores); // Exécuter l'algorithme
}

// Implémentation Pi.java
class PiJavaImplementation implements MonteCarloImplementation {
    @Override
    public String getName() {
        return "Pi.java";
    }

    @Override
    public double execute(int totalPoints, int numCores) {
        Master master = new Master();
        try {
            long circleCount = master.doRun(totalPoints / numCores, numCores);
            return 4.0 * circleCount / (totalPoints);
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }
}

// Implémentation Assignment102
class Assignment102Implementation implements MonteCarloImplementation {
    @Override
    public String getName() {
        return "Assignment102";
    }

    @Override
    public double execute(int totalPoints, int numCores) {
        tests.PiMonteCarlo piMonteCarlo = new tests.PiMonteCarlo(totalPoints, numCores);
        return piMonteCarlo.getPi();
    }
}
