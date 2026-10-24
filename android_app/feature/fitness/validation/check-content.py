"""Verify development catalog and every referenced local/archived asset without extracting files."""
from pathlib import Path
import hashlib
import json
import zipfile

root = Path(__file__).resolve().parents[4]
content = root / 'content/fitness'
properties = dict(line.split('=', 1) for line in (content / 'android/content.properties').read_text().splitlines() if line and not line.startswith('#'))
catalog = json.loads((content / 'android/catalog.json').read_text(encoding='utf-8'))
sources = json.loads((content / 'sources/free-exercise-db/exercises.json').read_text(encoding='utf-8'))
assert len(catalog['sourceRecords']) == len(sources) == int(properties['sourceCount']) == 876
assert len(catalog['exercises']) == int(properties['exerciseCount']) == 16
assert catalog['reviewedCount'] == int(properties['reviewedCount']) == 0
assert properties['releaseApproved'] == 'false'
assert catalog['sourceRevision'] == properties['sourceRevision']
source_by_id = {row['id']: row for row in sources}
assert len(source_by_id) == len(sources)
archive = root / properties['sourceArchive']
with archive.open('rb') as stream:
    digest = hashlib.file_digest(stream, 'sha256').hexdigest()
assert digest == properties['sourceArchiveSha256']
prefix = f"free-exercise-db-{properties['sourceRevision']}/exercises/"
images = 0
ids = set()
with zipfile.ZipFile(archive) as package:
    names = set(package.namelist())
    for row in catalog['sourceRecords']:
        assert row['id'] not in ids
        ids.add(row['id'])
        original = source_by_id[row['id']]
        assert row['name'] == original['name'] and row['instructions'] == original['instructions']
        assert [image['path'].removeprefix('source-media/') for image in row['images']] == original['images']
        for image in row['images']:
            assert image['path'].startswith('source-media/') and '..' not in image['path']
            assert prefix + image['path'].removeprefix('source-media/') in names
            images += 1
assert images == 1746
ids = set()
for row in catalog['exercises']:
    assert row['id'] not in ids and row['purpose'] in ('Strength', 'Cardio', 'Warmup', 'Stretch')
    ids.add(row['id'])
    assert row['unit'] in ('Reps', 'Seconds') and row['equipment'] and not row['reviewed']
    for image in row['images']:
        assert 0 <= image['panel'] < image['panels'] <= 4
        assert '..' not in image['path'] and (content / 'pilot' / image['path']).is_file()
for row in catalog['sourceRecords']:
    assert row.get('linkedExerciseId') is None or row['linkedExerciseId'] in ids
muscles = list((content / 'pilot/muscles').glob('*.svg'))
assert len(muscles) == 17
print(f'876 source definitions / {images} source pictures / 16 Chinese draft definitions / {len(muscles)} shared SVG files verified; source ZIP SHA-256 matched.')
print('Content consistency only; teaching/anatomy/translation approval remains 0.')
