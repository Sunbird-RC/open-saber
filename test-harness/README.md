Test file name: TeacherRecordTests.jmx

Steps
1. Add - Teacher
2. If response is failure due to "Authentication token is invalid", then
2.1 Authenticate and fetch the token
2.2 Re-attempt - Add Teacher (step 1)
3. Read the just added Teacher record
4. Update
5. Read after update and ensure the right value is reflected.
