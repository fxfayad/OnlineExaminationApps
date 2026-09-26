package com.myapps.onlineexaminationapps.firebase

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.myapps.onlineexaminationapps.model.Chapter
import com.myapps.onlineexaminationapps.model.Question

object CProgrammingDataSeeder {
    private val firestore = FirebaseFirestore.getInstance()
    private val chaptersCollection = firestore.collection("chapters")
    private val questionsCollection = firestore.collection("questions")

    fun seedCProgrammingCourse(onComplete: () -> Unit = {}) {
        chaptersCollection.document("c_prog_ch_1").get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    Log.d("CDataSeeder", "Seeding 10 C Programming Chapters and 20 Short Questions to Firestore...")
                    performSeeding(onComplete)
                } else {
                    Log.d("CDataSeeder", "C Programming chapters already seeded in Firestore.")
                    onComplete()
                }
            }
            .addOnFailureListener { e ->
                Log.e("CDataSeeder", "Failed to check chapter existence, attempting seeding...", e)
                performSeeding(onComplete)
            }
    }

    private fun performSeeding(onComplete: () -> Unit) {
        val batch = firestore.batch()

        val chapters = listOf(
            Chapter(
                id = "c_prog_ch_1",
                chapterId = "c_prog_ch_1",
                name = "Chapter 1: Introduction to C Programming",
                title = "Chapter 1: Introduction to C Programming",
                description = "Introduce students to the C programming language, its history, importance, features, applications, basic program structure, compiler, and the general process of writing and executing a C program.",
                createdBy = "system",
                teacherId = "system",
                createdAt = Timestamp.now()
            ),
            Chapter(
                id = "c_prog_ch_2",
                chapterId = "c_prog_ch_2",
                name = "Chapter 2: Variables, Data Types and Constants",
                title = "Chapter 2: Variables, Data Types and Constants",
                description = "Explain variables, constants, identifiers, keywords, and the basic data types used in C such as int, float, double, and char. Students should understand how data is stored and declared in a C program.",
                createdBy = "system",
                teacherId = "system",
                createdAt = Timestamp.now()
            ),
            Chapter(
                id = "c_prog_ch_3",
                chapterId = "c_prog_ch_3",
                name = "Chapter 3: Operators and Expressions",
                title = "Chapter 3: Operators and Expressions",
                description = "Explain arithmetic, relational, logical, assignment, increment, decrement, and other commonly used operators in C. Also introduce expressions and operator precedence.",
                createdBy = "system",
                teacherId = "system",
                createdAt = Timestamp.now()
            ),
            Chapter(
                id = "c_prog_ch_4",
                chapterId = "c_prog_ch_4",
                name = "Chapter 4: Input and Output",
                title = "Chapter 4: Input and Output",
                description = "Teach students how to take input from users and display output using standard C functions such as printf() and scanf(). Include format specifiers and basic input/output concepts.",
                createdBy = "system",
                teacherId = "system",
                createdAt = Timestamp.now()
            ),
            Chapter(
                id = "c_prog_ch_5",
                chapterId = "c_prog_ch_5",
                name = "Chapter 5: Conditional Statements",
                title = "Chapter 5: Conditional Statements",
                description = "Explain decision-making in C using if, if-else, nested if, else-if ladder, and switch statements. Students should learn how programs make decisions based on conditions.",
                createdBy = "system",
                teacherId = "system",
                createdAt = Timestamp.now()
            ),
            Chapter(
                id = "c_prog_ch_6",
                chapterId = "c_prog_ch_6",
                name = "Chapter 6: Loops and Control Statements",
                title = "Chapter 6: Loops and Control Statements",
                description = "Explain repetition and iteration using for, while, and do-while loops. Also introduce break and continue statements and explain how they control loop execution.",
                createdBy = "system",
                teacherId = "system",
                createdAt = Timestamp.now()
            ),
            Chapter(
                id = "c_prog_ch_7",
                chapterId = "c_prog_ch_7",
                name = "Chapter 7: Arrays and Strings",
                title = "Chapter 7: Arrays and Strings",
                description = "Introduce one-dimensional and two-dimensional arrays and explain how multiple values can be stored using arrays. Also explain strings, character arrays, and basic string operations.",
                createdBy = "system",
                teacherId = "system",
                createdAt = Timestamp.now()
            ),
            Chapter(
                id = "c_prog_ch_8",
                chapterId = "c_prog_ch_8",
                name = "Chapter 8: Functions",
                title = "Chapter 8: Functions",
                description = "Explain functions, function declaration, definition, calling a function, parameters, arguments, return values, and basic recursion. Students should understand how functions help organize and reuse code.",
                createdBy = "system",
                teacherId = "system",
                createdAt = Timestamp.now()
            ),
            Chapter(
                id = "c_prog_ch_9",
                chapterId = "c_prog_ch_9",
                name = "Chapter 9: Pointers and Structures",
                title = "Chapter 9: Pointers and Structures",
                description = "Introduce pointers, memory addresses, pointer variables, address operators, and basic pointer usage. Also explain structures and how structures can group different types of data.",
                createdBy = "system",
                teacherId = "system",
                createdAt = Timestamp.now()
            ),
            Chapter(
                id = "c_prog_ch_10",
                chapterId = "c_prog_ch_10",
                name = "Chapter 10: File Handling and Dynamic Memory",
                title = "Chapter 10: File Handling and Dynamic Memory",
                description = "Introduce file handling in C, including opening, reading, writing, and closing files using functions such as fopen(), fclose(), fprintf(), and fscanf(). Also provide an introduction to dynamic memory allocation using malloc().",
                createdBy = "system",
                teacherId = "system",
                createdAt = Timestamp.now()
            )
        )

        for (chap in chapters) {
            batch.set(chaptersCollection.document(chap.id), chap)
        }

        val questions = listOf(
            // Chapter 1
            Question(
                id = "c_prog_q_1_1",
                questionId = "c_prog_q_1_1",
                chapterId = "c_prog_ch_1",
                teacherId = "system",
                createdBy = "system",
                questionText = "What is C programming language?",
                question = "What is C programming language?",
                questionType = "SHORT",
                type = "short",
                expectedAnswer = "C is a powerful, general-purpose procedural computer programming language created by Dennis Ritchie at Bell Labs in 1972.",
                answer = "C is a powerful, general-purpose procedural computer programming language created by Dennis Ritchie at Bell Labs in 1972.",
                marks = 5,
                createdAt = Timestamp.now()
            ),
            Question(
                id = "c_prog_q_1_2",
                questionId = "c_prog_q_1_2",
                chapterId = "c_prog_ch_1",
                teacherId = "system",
                createdBy = "system",
                questionText = "What are the main features of C?",
                question = "What are the main features of C?",
                questionType = "SHORT",
                type = "short",
                expectedAnswer = "Main features include simplicity, portability, efficiency, structured modular programming, pointer support for low-level memory access, and rich library functions.",
                answer = "Main features include simplicity, portability, efficiency, structured modular programming, pointer support for low-level memory access, and rich library functions.",
                marks = 5,
                createdAt = Timestamp.now()
            ),

            // Chapter 2
            Question(
                id = "c_prog_q_2_1",
                questionId = "c_prog_q_2_1",
                chapterId = "c_prog_ch_2",
                teacherId = "system",
                createdBy = "system",
                questionText = "What is a variable in C? Give an example.",
                question = "What is a variable in C? Give an example.",
                questionType = "SHORT",
                type = "short",
                expectedAnswer = "A variable is a named memory location used to store a value that can be modified during program execution. Example: int age = 20;",
                answer = "A variable is a named memory location used to store a value that can be modified during program execution. Example: int age = 20;",
                marks = 5,
                createdAt = Timestamp.now()
            ),
            Question(
                id = "c_prog_q_2_2",
                questionId = "c_prog_q_2_2",
                chapterId = "c_prog_ch_2",
                teacherId = "system",
                createdBy = "system",
                questionText = "What are the basic data types in C?",
                question = "What are the basic data types in C?",
                questionType = "SHORT",
                type = "short",
                expectedAnswer = "The basic data types in C are int (integer), float (floating-point number), double (double-precision floating-point), and char (single character).",
                answer = "The basic data types in C are int (integer), float (floating-point number), double (double-precision floating-point), and char (single character).",
                marks = 5,
                createdAt = Timestamp.now()
            ),

            // Chapter 3
            Question(
                id = "c_prog_q_3_1",
                questionId = "c_prog_q_3_1",
                chapterId = "c_prog_ch_3",
                teacherId = "system",
                createdBy = "system",
                questionText = "What are arithmetic operators in C?",
                question = "What are arithmetic operators in C?",
                questionType = "SHORT",
                type = "short",
                expectedAnswer = "Arithmetic operators perform mathematical calculations: + (addition), - (subtraction), * (multiplication), / (division), and % (modulus/remainder).",
                answer = "Arithmetic operators perform mathematical calculations: + (addition), - (subtraction), * (multiplication), / (division), and % (modulus/remainder).",
                marks = 5,
                createdAt = Timestamp.now()
            ),
            Question(
                id = "c_prog_q_3_2",
                questionId = "c_prog_q_3_2",
                chapterId = "c_prog_ch_3",
                teacherId = "system",
                createdBy = "system",
                questionText = "What is the difference between = and == in C?",
                question = "What is the difference between = and == in C?",
                questionType = "SHORT",
                type = "short",
                expectedAnswer = "The '=' operator is an assignment operator used to store values, while '==' is a relational equality operator that compares two expressions.",
                answer = "The '=' operator is an assignment operator used to store values, while '==' is a relational equality operator that compares two expressions.",
                marks = 5,
                createdAt = Timestamp.now()
            ),

            // Chapter 4
            Question(
                id = "c_prog_q_4_1",
                questionId = "c_prog_q_4_1",
                chapterId = "c_prog_ch_4",
                teacherId = "system",
                createdBy = "system",
                questionText = "What is the purpose of printf() in C?",
                question = "What is the purpose of printf() in C?",
                questionType = "SHORT",
                type = "short",
                expectedAnswer = "printf() is a standard output function in stdio.h used to print formatted text and variable values to the console screen.",
                answer = "printf() is a standard output function in stdio.h used to print formatted text and variable values to the console screen.",
                marks = 5,
                createdAt = Timestamp.now()
            ),
            Question(
                id = "c_prog_q_4_2",
                questionId = "c_prog_q_4_2",
                chapterId = "c_prog_ch_4",
                teacherId = "system",
                createdBy = "system",
                questionText = "What is the purpose of scanf() in C?",
                question = "What is the purpose of scanf() in C?",
                questionType = "SHORT",
                type = "short",
                expectedAnswer = "scanf() is a standard input function in stdio.h used to read formatted user input from the keyboard and store it in memory locations.",
                answer = "scanf() is a standard input function in stdio.h used to read formatted user input from the keyboard and store it in memory locations.",
                marks = 5,
                createdAt = Timestamp.now()
            ),

            // Chapter 5
            Question(
                id = "c_prog_q_5_1",
                questionId = "c_prog_q_5_1",
                chapterId = "c_prog_ch_5",
                teacherId = "system",
                createdBy = "system",
                questionText = "What is an if-else statement in C?",
                question = "What is an if-else statement in C?",
                questionType = "SHORT",
                type = "short",
                expectedAnswer = "An if-else statement is a decision-making control structure that executes the 'if' block if a condition evaluates to true, and the 'else' block if false.",
                answer = "An if-else statement is a decision-making control structure that executes the 'if' block if a condition evaluates to true, and the 'else' block if false.",
                marks = 5,
                createdAt = Timestamp.now()
            ),
            Question(
                id = "c_prog_q_5_2",
                questionId = "c_prog_q_5_2",
                chapterId = "c_prog_ch_5",
                teacherId = "system",
                createdBy = "system",
                questionText = "What is the purpose of the switch statement?",
                question = "What is the purpose of the switch statement?",
                questionType = "SHORT",
                type = "short",
                expectedAnswer = "The switch statement is a multi-way branch selection control structure that tests an expression against multiple matching case values.",
                answer = "The switch statement is a multi-way branch selection control structure that tests an expression against multiple matching case values.",
                marks = 5,
                createdAt = Timestamp.now()
            ),

            // Chapter 6
            Question(
                id = "c_prog_q_6_1",
                questionId = "c_prog_q_6_1",
                chapterId = "c_prog_ch_6",
                teacherId = "system",
                createdBy = "system",
                questionText = "What is a loop? Name three types of loops in C.",
                question = "What is a loop? Name three types of loops in C.",
                questionType = "SHORT",
                type = "short",
                expectedAnswer = "A loop repeatedly executes a block of statements as long as a specified condition is true. The 3 types in C are for loop, while loop, and do-while loop.",
                answer = "A loop repeatedly executes a block of statements as long as a specified condition is true. The 3 types in C are for loop, while loop, and do-while loop.",
                marks = 5,
                createdAt = Timestamp.now()
            ),
            Question(
                id = "c_prog_q_6_2",
                questionId = "c_prog_q_6_2",
                chapterId = "c_prog_ch_6",
                teacherId = "system",
                createdBy = "system",
                questionText = "What is the difference between break and continue?",
                question = "What is the difference between break and continue?",
                questionType = "SHORT",
                type = "short",
                expectedAnswer = "The 'break' statement terminates loop execution completely and exits, whereas 'continue' skips remaining statements in the current iteration and jumps to the next iteration.",
                answer = "The 'break' statement terminates loop execution completely and exits, whereas 'continue' skips remaining statements in the current iteration and jumps to the next iteration.",
                marks = 5,
                createdAt = Timestamp.now()
            ),

            // Chapter 7
            Question(
                id = "c_prog_q_7_1",
                questionId = "c_prog_q_7_1",
                chapterId = "c_prog_ch_7",
                teacherId = "system",
                createdBy = "system",
                questionText = "What is an array in C?",
                question = "What is an array in C?",
                questionType = "SHORT",
                type = "short",
                expectedAnswer = "An array is a fixed-size contiguous sequence of elements of the same data type stored under a single identifier name.",
                answer = "An array is a fixed-size contiguous sequence of elements of the same data type stored under a single identifier name.",
                marks = 5,
                createdAt = Timestamp.now()
            ),
            Question(
                id = "c_prog_q_7_2",
                questionId = "c_prog_q_7_2",
                chapterId = "c_prog_ch_7",
                teacherId = "system",
                createdBy = "system",
                questionText = "What is a string in C?",
                question = "What is a string in C?",
                questionType = "SHORT",
                type = "short",
                expectedAnswer = "A string in C is a sequence of characters stored in a one-dimensional character array terminated by a null character ('\\0').",
                answer = "A string in C is a sequence of characters stored in a one-dimensional character array terminated by a null character ('\\0').",
                marks = 5,
                createdAt = Timestamp.now()
            ),

            // Chapter 8
            Question(
                id = "c_prog_q_8_1",
                questionId = "c_prog_q_8_1",
                chapterId = "c_prog_ch_8",
                teacherId = "system",
                createdBy = "system",
                questionText = "What is a function in C?",
                question = "What is a function in C?",
                questionType = "SHORT",
                type = "short",
                expectedAnswer = "A function is a self-contained block of reusable code designed to perform a specific task when called in a program.",
                answer = "A function is a self-contained block of reusable code designed to perform a specific task when called in a program.",
                marks = 5,
                createdAt = Timestamp.now()
            ),
            Question(
                id = "c_prog_q_8_2",
                questionId = "c_prog_q_8_2",
                chapterId = "c_prog_ch_8",
                teacherId = "system",
                createdBy = "system",
                questionText = "What is the difference between a function parameter and an argument?",
                question = "What is the difference between a function parameter and an argument?",
                questionType = "SHORT",
                type = "short",
                expectedAnswer = "Parameters are variables declared in the function signature header, whereas arguments are actual values or variables passed to the function when invoked.",
                answer = "Parameters are variables declared in the function signature header, whereas arguments are actual values or variables passed to the function when invoked.",
                marks = 5,
                createdAt = Timestamp.now()
            ),

            // Chapter 9
            Question(
                id = "c_prog_q_9_1",
                questionId = "c_prog_q_9_1",
                chapterId = "c_prog_ch_9",
                teacherId = "system",
                createdBy = "system",
                questionText = "What is a pointer in C?",
                question = "What is a pointer in C?",
                questionType = "SHORT",
                type = "short",
                expectedAnswer = "A pointer is a variable that stores the memory address location of another variable in computer memory.",
                answer = "A pointer is a variable that stores the memory address location of another variable in computer memory.",
                marks = 5,
                createdAt = Timestamp.now()
            ),
            Question(
                id = "c_prog_q_9_2",
                questionId = "c_prog_q_9_2",
                chapterId = "c_prog_ch_9",
                teacherId = "system",
                createdBy = "system",
                questionText = "What is a structure in C?",
                question = "What is a structure in C?",
                questionType = "SHORT",
                type = "short",
                expectedAnswer = "A structure ('struct') is a user-defined custom data type that allows grouping related variables of different data types into a single unit.",
                answer = "A structure ('struct') is a user-defined custom data type that allows grouping related variables of different data types into a single unit.",
                marks = 5,
                createdAt = Timestamp.now()
            ),

            // Chapter 10
            Question(
                id = "c_prog_q_10_1",
                questionId = "c_prog_q_10_1",
                chapterId = "c_prog_ch_10",
                teacherId = "system",
                createdBy = "system",
                questionText = "What is file handling in C?",
                question = "What is file handling in C?",
                questionType = "SHORT",
                type = "short",
                expectedAnswer = "File handling refers to reading, writing, creating, and manipulating permanent files stored on disk using standard library functions like fopen(), fprintf(), and fclose().",
                answer = "File handling refers to reading, writing, creating, and manipulating permanent files stored on disk using standard library functions like fopen(), fprintf(), and fclose().",
                marks = 5,
                createdAt = Timestamp.now()
            ),
            Question(
                id = "c_prog_q_10_2",
                questionId = "c_prog_q_10_2",
                chapterId = "c_prog_ch_10",
                teacherId = "system",
                createdBy = "system",
                questionText = "What is the purpose of malloc() in C?",
                question = "What is the purpose of malloc() in C?",
                questionType = "SHORT",
                type = "short",
                expectedAnswer = "malloc() (memory allocation) dynamically allocates a contiguous block of specified bytes in heap memory during runtime and returns a void pointer to the first byte.",
                answer = "malloc() (memory allocation) dynamically allocates a contiguous block of specified bytes in heap memory during runtime and returns a void pointer to the first byte.",
                marks = 5,
                createdAt = Timestamp.now()
            )
        )

        for (q in questions) {
            batch.set(questionsCollection.document(q.id), q)
        }

        batch.commit()
            .addOnSuccessListener {
                Log.d("CDataSeeder", "Successfully seeded 10 C Programming Chapters and 20 Short Questions!")
                onComplete()
            }
            .addOnFailureListener { e ->
                Log.e("CDataSeeder", "Failed to commit C Programming seeding batch", e)
                onComplete()
            }
    }
}
