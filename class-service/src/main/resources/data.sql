-- Jeu de donnees de demonstration : dates relatives pour que les cours soient toujours a venir
-- et que la collection Postman soit jouable immediatement apres le demarrage.
INSERT INTO fitness_classes
  (version, name, description, instructor, gym_location, category, level,
   duration_minutes, max_participants, current_participants, price, date_time, status)
VALUES
  (0, 'Yoga Vinyasa', 'Enchainements fluides et respiration', 'Marie Dupont', 'Paris 11e',
   'YOGA', 'BEGINNER', 60, 20, 0, 15.00, DATEADD('DAY', 3, CURRENT_TIMESTAMP), 'SCHEDULED'),
  (0, 'CrossFit WOD', 'Circuit haute intensite', 'Karim Benali', 'Paris 15e',
   'CROSSFIT', 'ADVANCED', 45, 12, 0, 22.50, DATEADD('DAY', 4, CURRENT_TIMESTAMP), 'SCHEDULED'),
  (0, 'Zumba Party', 'Cardio danse sur musique latine', 'Ana Silva', 'Lyon 3e',
   'ZUMBA', 'BEGINNER', 60, 30, 0, 12.00, DATEADD('DAY', 5, CURRENT_TIMESTAMP), 'SCHEDULED'),
  (0, 'Pilates Reformer', 'Renforcement profond sur machine', 'Marie Dupont', 'Paris 11e',
   'PILATES', 'INTERMEDIATE', 45, 8, 0, 30.00, DATEADD('DAY', 6, CURRENT_TIMESTAMP), 'SCHEDULED'),
  (0, 'Spinning Endurance', 'Sortie longue sur home trainer', 'Luc Moreau', 'Lyon 6e',
   'SPINNING', 'INTERMEDIATE', 90, 25, 0, 18.00, DATEADD('DAY', 7, CURRENT_TIMESTAMP), 'SCHEDULED'),
  -- Cours presque complet : permet de tester le 409 de surreservation
  (0, 'Boxing Sparring', 'Technique et opposition legere', 'Karim Benali', 'Paris 15e',
   'BOXING', 'ADVANCED', 90, 10, 9, 25.00, DATEADD('DAY', 2, CURRENT_TIMESTAMP), 'SCHEDULED'),
  -- Cours dans moins de 24h : permet de tester l'annulation hors delai
  (0, 'Yoga Reveil', 'Seance douce du matin', 'Ana Silva', 'Paris 11e',
   'YOGA', 'BEGINNER', 30, 15, 0, 10.00, DATEADD('HOUR', 12, CURRENT_TIMESTAMP), 'SCHEDULED');
