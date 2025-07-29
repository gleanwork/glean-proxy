# General code rules. ALWAYS CONSIDER AS HIGHEST PRIORITY
- LLMs add explanatory comments to code that are obvious to experienced developers. NEVER do that. Remove those before suggesting code. In fact, when in doubt, do not add comments at all. The only type of comments that are okay are "why" comments that explain certain choices, but do not describe what the code is doing
- HIGHEST PRIORITY. No seriously, we absolutely do not want obvious comments that are meant to educate junior developers checked into code. Always evaluate your code output, and do another pass to remove them if you do generate them. Our developers should not have to call you out.
- prefer inlining code into fewer lines. No single-use variables just to be used again in the next few lines

# Generated test styles
- PREFER TO INLINE LITERALS FOR EVERYTHING. DUPLICATION IS EXPECTED for test function calls and asserted values
- BAD var expectedResponse = {}, assert(actualResponse, expectedResponse)
- GOOD not inlined: assert(actualResponse, {})
- HIGHEST PRIORITY. Seriously, if your output test code is found to not inline, regenerate the test code. The developer should not have to remind you.
- Before mocking, consider creating a fake implementation inline in the test file. Example: rather than mock low level json.loads/dumps, consider patching a higher level fake gcs.upload/download that keeps state in memory
- NEVER OVERMOCK. You are not trying to copy the implementation exactly. Create resilient tests that only test behavior. Consider having to add any mocks a failure. Prefer testing real objects, or by using fake objects

# Code cleanup AKA /code clean
- If you somehow failed to follow the above rules, modify the code you just generated to abide by the general code rules and generated test styles list
