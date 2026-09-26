"""
CapCoach - Synthetic Dataset Generator (v2)
Week 1 deliverable for Member 3 (ML Engineer)

v2 changes, following the feature-importance audit:
  1. Duration_Minutes now affects the label (long low-priority tasks shed first)
  2. Task_Priority_Weight is INDEPENDENT of category - the user sets it per
     task, so a 40%-weighted group project and a discussion post are both
     Academic but not equally protected
  3. Category_ID replaced by a binary Is_Recovery_Activity flag: recovery
     activities act as a protective buffer rather than load, per the
     social-support burnout research. Tagging a NEW category as recovery
     later needs a database flag, not a model retrain.
  4. Rows raised 1000 -> 3000 so rare branches get enough examples to learn

Features:
- Recovery_Debt_Score  : 0-100 (computed Java-side from Health Connect sleep data)
- Days_Until_Due       : 0-14
- Task_Priority_Weight : 3 High, 2 Medium, 1 Low  (user-set, per task)
- Is_Fixed_Time        : 1 fixed (shift/exam/class), 0 flexible
- Is_Recovery_Activity : 1 if the activity restores capacity (Social today;
                         Fitness / Meditation / Family later), else 0
- Duration_Minutes     : 30-180

Label: 1 = KEEP, 0 = MOVE
"""

import numpy as np
import pandas as pd

np.random.seed(42)
N = 3000

ACADEMIC, WORK, ERRAND, SOCIAL, COCURRICULAR = 0, 1, 2, 3, 4

recovery_debt = np.random.randint(0, 101, N)
days_until_due = np.random.randint(0, 15, N)
category_id = np.random.randint(0, 5, N)
duration_minutes = np.random.choice([30, 60, 90, 120, 180], size=N)

# --- Priority is now drawn independently, only *skewed* by category ---------
# Academic work skews high-priority, errands skew low, but any category can
# carry any priority. This breaks the 1-to-1 mapping that made the two
# columns duplicates of each other in v1.
priority_dist = {
    ACADEMIC:     [0.10, 0.30, 0.60],   # P(low), P(med), P(high)
    WORK:         [0.15, 0.45, 0.40],
    ERRAND:       [0.60, 0.30, 0.10],
    SOCIAL:       [0.60, 0.30, 0.10],
    COCURRICULAR: [0.30, 0.50, 0.20],
}
task_priority_weight = np.array([
    np.random.choice([1, 2, 3], p=priority_dist[c]) for c in category_id
])

fixed_prob = np.where(category_id == WORK, 0.9,
             np.where(category_id == ACADEMIC, 0.3, 0.1))
is_fixed_time = (np.random.rand(N) < fixed_prob).astype(int)

# --- Recovery flag ----------------------------------------------------------
# The model never sees the category id itself, only whether that category is
# tagged as recovery. Today only Social qualifies; adding Fitness or
# Meditation later is a row in the Category table, not a retrain.
RECOVERY_CATEGORIES = {SOCIAL}
is_recovery_activity = np.array(
    [1 if c in RECOVERY_CATEGORIES else 0 for c in category_id])


def label_row(debt, days, fixed, priority, is_recovery, duration):
    """The 'perfect teacher' rule the Decision Tree must learn."""

    # 1. Immovable commitments are never surrendered.
    if fixed == 1:
        return 1

    # 2. Recovery activities are a protective buffer, not load. Under heavy
    #    debt we keep them rather than shed them - isolating an exhausted
    #    student makes burnout worse, not better.
    if is_recovery == 1 and debt > 80:
        return 1

    # 3. High-priority work that is imminent is protected on its own merits.
    if priority == 3 and days <= 2:
        return 1

    # 4. Heavy recovery debt - shed aggressively.
    if debt > 80:
        if days > 2:
            return 0                              # slack exists, move it
        if duration >= 120 and priority <= 2:
            return 0                              # long and not critical
        return 1

    # 5. Moderate debt - shed only the clearly deferrable.
    if debt > 60:
        if priority == 1 and days > 5:
            return 0
        if duration >= 180 and priority <= 2 and days > 3:
            return 0
        return 1

    # 6. Student has capacity. Keep everything.
    return 1


labels = [
    label_row(d, du, f, p, r, dur)
    for d, du, f, p, r, dur in zip(
        recovery_debt, days_until_due, is_fixed_time,
        task_priority_weight, is_recovery_activity, duration_minutes)
]

df = pd.DataFrame({
    "Recovery_Debt_Score": recovery_debt,
    "Days_Until_Due": days_until_due,
    "Task_Priority_Weight": task_priority_weight,
    "Is_Fixed_Time": is_fixed_time,
    "Is_Recovery_Activity": is_recovery_activity,
    "Duration_Minutes": duration_minutes,
    "Label": labels,
    # Diagnostic only - NOT a model feature. Kept so the team can inspect
    # results by category without the model ever seeing the raw id.
    "_Category_ID_debug": category_id,
})

df.to_csv("capcoach_synthetic_tasks.csv", index=False)

print(f"Saved {len(df)} rows to capcoach_synthetic_tasks.csv")
print("\nLabel balance (1=Keep, 0=Move):")
print(df["Label"].value_counts().to_string())
print(f"Move rate: {(1 - df['Label'].mean()) * 100:.1f}%")

print("\nSanity - priority is no longer derivable from category:")
print(df.groupby("_Category_ID_debug")["Task_Priority_Weight"].nunique().to_string())
print("\nRecovery activities tagged:", int(df["Is_Recovery_Activity"].sum()))