# Score Saving Process - Detailed Analysis

## Current Flow

### 1. Frontend (reports.html)
- **Lines 1710-1730**: Parse scoring scheme and extract components using **alias** (or name if no alias)
  ```javascript
  const displayName = item.alias || item.name;
  components.add(displayName);  // Adds "CA1", "CA2", "Exam"
  ```

- **Lines 1830-1860**: Create score input elements with `data-component` set to the alias/name
  ```javascript
  <input type="number" class="score-input" 
         data-component="${c}"  // c is "CA1", "CA2", "Exam"
         value="${displayValue}" ...>
  ```

- **Lines 2034-2050**: Collect scores when saving
  ```javascript
  row.querySelectorAll('.score-input').forEach(input => {
      const componentName = input.dataset.component;  // "CA1"
      const val = parseInt(input.value) || 0;
      subjectScores[componentName] = val;  // {CA1: 20, CA2: 15, Exam: 45}
  });
  ```

### 2. Backend Receiving (AssessmentReportController.kt)
- **Lines 608-900**:  Receives `SaveAssessmentRequest` with `scores: List<ScoreInput>`
- **Lines 825-830**: Gets scoring scheme and parses aliases
  ```kotlin
  val aliasMappings = parseScoringSchemeAliases(studentEnrollment.schoolClass.scoringScheme)
  ```

### 3. Alias Mapping Functions (Lines 1405-1443)

**parseScoringSchemeAliases():**
```kotlin
// Input: "[{name: "1st CA", alias: "CA1", max: 20}, ...]"
// Output: {"1st ca" -> "CA1", "2nd ca" -> "CA2", "exam" -> "Exam"}
val aliases = mutableMapOf<String, String>()
scheme.forEach { item ->
    val name = (item["name"] as? String) ?: return@forEach
    val alias = (item["alias"] as? String) ?: name
    aliases[name.lowercase()] = alias  // Maps name → alias
}
```

**normalizeScoreKeys():**
```kotlin
// Input: {CA1: 20, CA2: 15, Exam: 45}, aliasMappings: {"1st ca" -> "CA1", "2nd ca" -> "CA2", "exam" -> "Exam"}
// Issue: Incoming keys are ALREADY ALIASES, but mapping is name → alias
scores.forEach { (key, value) ->
    val keyLower = key.lowercase()  // "ca1"
    val alias = aliasMappings.entries.find { 
        keyLower.contains(it.key) || it.key.contains(keyLower)  // "ca1" contains "1st ca"? NO!
    }?.value ?: key  // Falls back to key itself (CA1)
    normalized[alias] = value
}
```

## Problem Identified

### Issue: Incorrect Alias Mapping Lookup

When scores are sent from frontend:
- Incoming: `{CA1: 20, CA2: 15, Exam: 45}` (using aliases)

When backend processes:
- aliasMappings: `{"1st ca" -> "CA1", "2nd ca" -> "CA2", "exam" -> "Exam"}`
- `normalizeScoreKeys()` tries to match "ca1" against keys like "1st ca"
- The substring matching fails: `"ca1".contains("1st ca")` = false, `"1st ca".contains("ca1")` = false
- Falls back to original key "CA1"
- Result: `{CA1: 20, CA2: 15, Exam: 45}` - **correct by accident**

**However, there are edge cases that could break:**

1. **If incoming keys use different format than aliases**: e.g., incoming has "1st CA" but alias is "CA1"
   - `"1st ca".contains("ca1")` or `"ca1".contains("1st ca")` - still fails

2. **If names and aliases are very different**: e.g., name="Continuous Assessment 1", alias="CA1"
   - aliasMappings: `{"continuous assessment 1" -> "CA1"}`
   - incoming key: `"CA1"`
   - No match found

## Current Database Storage

The function **should be storing** (after normalization):
- **Key**: Alias (e.g., "CA1", "CA2", "Exam")
- **Value**: Score (e.g., 20, 15, 45)
- **Saved to**: `SubjectScore.scoresJson` as JSON

**Actual storage (in current code):**
- Often falls back to original keys due to matching failure
- Alias normalization is bypassed

## Verification Needed

1. ✅ Scores are being saved to `SubjectScore.scoresJson`
2. ✅ Score association with subject is correct
3. ✅ Score association with student is correct  
4. ❌ **Score keys are normalized to aliases** - This needs verification

## Recommendation

The normalization logic should be inverted. Instead of:
```kotlin
// Current: Try to match incoming key against name mapping
val alias = aliasMappings.entries.find { 
    keyLower.contains(it.key) || it.key.contains(keyLower)
}?.value ?: key
```

Should be:
```kotlin
// Better: Check if incoming key matches any value (alias) or any key (name)
// If it's already an alias, keep it. If it's a name, map it to the alias.
val normalized: String = aliasMappings.entries.find { (name, alias) ->
    keyLower == name || keyLower == alias.lowercase()
}?.value ?: key  // Keep as-is if no match (defensive)
```

This ensures:
- If key is already an alias (e.g., "CA1"), it remains "CA1"
- If key is a name (e.g., "1st CA"), it gets mapped to alias "CA1"
- If no match, keep original (defensive fallback)
