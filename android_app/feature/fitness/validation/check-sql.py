"""Prepare the actual DAO SQL against a temporary SQLite schema derived from Entity fields.

This checks SQLite SQL/field compatibility and aggregate semantics. It does not replace
Room's KSP schema generation, Android SQLite versions, migrations or device tests.
"""
from pathlib import Path
import re
import sqlite3

base = Path(__file__).resolve().parents[1] / 'src/main/kotlin/com/pockettoolbox/feature/fitness/data'
source = (base / 'FitnessEntities.kt').read_text(encoding='utf-8')
entities = list(re.finditer(r'@Entity\((.*?)\)\s*data class (Fitness\w+Row)\s*\((.*?)\)\s*(?=\n)', source, re.S))
assert len(entities) == 9, 'Entity parser needs updating for changed Kotlin structure'
tables = {m[2]: re.search(r'tableName\s*=\s*"([^"]+)"', m[1])[1] for m in entities}
database = sqlite3.connect(':memory:')
database.row_factory = sqlite3.Row
database.execute('PRAGMA foreign_keys = ON')
fields = {}
for entity in entities:
    annotation, class_name, body = entity.groups()
    table = tables[class_name]
    fields[table] = [(m[1], m[2], bool(m[3])) for m in re.finditer(r'val (\w+): (String|Int|Long)(\?)?', body)]
    assert fields[table], table
    primary = re.findall(r'"([^"]+)"', re.search(r'primaryKeys\s*=\s*\[([^]]+)\]', annotation)[1])
    columns = [f'"{name}" {"TEXT" if kind == "String" else "INTEGER"}{"" if nullable else " NOT NULL"}' for name, kind, nullable in fields[table]]
    columns.append('PRIMARY KEY (' + ','.join(f'"{key}"' for key in primary) + ')')
    for foreign in re.finditer(r'ForeignKey\(entity = (\w+)::class, parentColumns = \[([^]]+)\], childColumns = \[([^]]+)\], onDelete = ForeignKey.CASCADE\)', annotation):
        columns.append(f'FOREIGN KEY ({foreign[3]}) REFERENCES {tables[foreign[1]]} ({foreign[2]}) ON DELETE CASCADE')
    database.execute(f'CREATE TABLE {table} (' + ','.join(columns) + ')')
    for index, item in enumerate(re.finditer(r'Index\((.*?)\)', annotation)):
        names = re.findall(r'"([^"]+)"', item[1])
        database.execute(f'CREATE {"UNIQUE " if "unique = true" in item[1] else ""}INDEX idx_{table}_{index} ON {table} (' + ','.join(f'"{name}"' for name in names) + ')')

dao = (base / 'FitnessDao.kt').read_text(encoding='utf-8')
queries = {}
for query in re.finditer(r'@Query\((?:"""(.*?)"""|"((?:[^"\\]|\\.)*)")\)', dao, re.S):
    sql = query[1] if query[1] is not None else query[2]
    method = re.search(r'fun\s+(\w+)', dao[query.end():])[1]
    params = {key: None for key in re.findall(r':(\w+)', sql)}
    database.execute('EXPLAIN ' + sql, params).fetchall()
    queries[method] = sql
assert len(queries) >= 35

def insert(table, **values):
    payload = {}
    for name, kind, nullable in fields[table]:
        payload[name] = values.get(name, None if nullable else ('' if kind == 'String' else 0))
    database.execute(f'INSERT INTO {table} (' + ','.join(f'"{key}"' for key in payload) + ') VALUES (' + ','.join('?' for _ in payload) + ')', list(payload.values()))

insert('fitness_plans', id='p', name='test', start='2026-10-23', end='2026-10-24', status='Active')
for day in ('2026-10-23', '2026-10-24'):
    insert('fitness_days', planId='p', date=day, kind='Training', parts='[]')
for identity, date in [('i', '2026-10-24'), ('c', '2026-10-24'), ('w', '2026-10-23')]:
    insert('fitness_items', id=identity, planId='p', date=date, exerciseId=identity)
for seq, identity, date, purpose, status, actual in [
    (1, 'i', '2026-10-24', 'Strength', 'Valid', 9),
    (2, 'i', '2026-10-24', 'Strength', 'Valid', None),
    (3, 'i', '2026-10-24', 'Strength', 'Revoked', 20),
    (4, 'c', '2026-10-24', 'Cardio', 'Valid', 60),
    (5, 'w', '2026-10-23', 'Warmup', 'Valid', None),
]:
    insert('fitness_events', id=str(seq), requestId=str(seq), sequence=seq, planId='p', trainingDate=date, itemId=identity,
           exerciseId=identity, exerciseName=identity, purpose=purpose, primaryPart='Chest' if purpose == 'Strength' else None,
           status=status, unit='Reps' if purpose == 'Strength' else 'Seconds', side='Total', actualTotal=actual)
    if purpose == 'Strength':
        for part in ('Chest', 'Shoulder'):
            insert('fitness_event_parts', eventId=str(seq), part=part)
for part in ('Chest', 'Shoulder', 'Legs'):
    insert('fitness_parts', id=part)

params = dict(start='2026-10-23', end='2026-10-24', today='2026-10-24', date='2026-10-24', planId=None, purpose=None,
              limit=200, exerciseId=None, part=None, unit=None, side=None)
summary = database.execute(queries['observeSummary'], params).fetchone()
assert tuple(summary) == (1, 2, 1, 3), dict(summary)
counts = database.execute(queries['observeExerciseCounts'], params).fetchall()
strength = next(row for row in counts if row['purpose'] == 'Strength')
assert (strength['days'], strength['segments'], strength['actualTotal'], strength['totalFilled']) == (1, 2, 9, 1)
database.execute("UPDATE fitness_events SET exerciseName = 'latest name' WHERE sequence = 2")
assert next(row for row in database.execute(queries['observeExerciseCounts'], params) if row['purpose'] == 'Strength')['name'] == 'latest name'
parts = database.execute(queries['observePartCounts'], params).fetchall()
assert {row['key']: row['segments'] for row in parts} == {'Chest': 2, 'Cardio': 1, 'Warmup': 1}
assert len(database.execute(queries['observeEvents'], params | {'part': 'Chest', 'purpose': 'Strength'}).fetchall()) == 2
assert len(database.execute(queries['observeEvents'], params | {'unit': 'Seconds'}).fetchall()) == 2
assert [row[0] for row in database.execute(queries['observeTrainingDates'], params)] == ['2026-10-24']
database.execute('DELETE FROM fitness_plans WHERE id = ?', ('p',))
assert database.execute('SELECT COUNT(*) FROM fitness_events').fetchone()[0] == 0
assert database.execute('SELECT COUNT(*) FROM fitness_event_parts').fetchone()[0] == 0
print(f'SQLite {sqlite3.sqlite_version}: {len(entities)} Entity structures, {len(queries)} DAO queries prepared; aggregate/scope/cascade fixtures passed.')
print('Not Room/KSP/Android execution validation.')
