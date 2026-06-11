import os
import glob
import time

history_dirs = [
    "/home/abuhaneefayn/.config/Code/User/History",
    "/home/abuhaneefayn/.config/Antigravity IDE/User/History",
    "/home/abuhaneefayn/.config/Antigravity/User/History",
    "/home/abuhaneefayn/.config/Kiro/User/History"
]

repos = []
apis = []

for hdir in history_dirs:
    if not os.path.exists(hdir):
        continue
    print("Scanning:", hdir)
    kt_files = glob.glob(os.path.join(hdir, "**/*.kt"), recursive=True)
    for p in kt_files:
        try:
            with open(p, "r", encoding="utf-8", errors="ignore") as f:
                first_line = f.readline()
                if "package com.haneef.school.data.repository" in first_line:
                    st = os.stat(p)
                    mtime_str = time.strftime('%Y-%m-%d %H:%M:%S', time.localtime(st.st_mtime))
                    repos.append((st.st_mtime, p, st.st_size, mtime_str))
                elif "package com.haneef.school.data.api" in first_line:
                    st = os.stat(p)
                    mtime_str = time.strftime('%Y-%m-%d %H:%M:%S', time.localtime(st.st_mtime))
                    apis.append((st.st_mtime, p, st.st_size, mtime_str))
        except Exception as e:
            pass

repos.sort()
print(f"\nFound {len(repos)} repository history entries.")
for mtime, p, size, mtime_str in repos[-5:]:
    print(f"File: {p} | Size: {size} bytes | Modified: {mtime_str}")

apis.sort()
print(f"\nFound {len(apis)} api service history entries.")
for mtime, p, size, mtime_str in apis[-5:]:
    print(f"File: {p} | Size: {size} bytes | Modified: {mtime_str}")

# Restore the absolutely LATEST repository
if repos:
    # Sort by mtime descending
    latest_repo = sorted(repos, key=lambda x: x[0], reverse=True)[0]
    print(f"\nRestoring repository to original from {latest_repo[1]} (Size: {latest_repo[2]} bytes, Modified: {latest_repo[3]})...")
    dest_repo = "/home/abuhaneefayn/Desktop/4school/4school/app/src/main/java/com/haneef/school/data/repository/SchoolRepository.kt"
    os.makedirs(os.path.dirname(dest_repo), exist_ok=True)
    with open(latest_repo[1], "r", encoding="utf-8") as src, open(dest_repo, "w", encoding="utf-8") as dest:
        dest.write(src.read())
    print("Repository restored successfully.")

# Restore the absolutely LATEST api service
if apis:
    # Sort by mtime descending
    latest_api = sorted(apis, key=lambda x: x[0], reverse=True)[0]
    print(f"\nRestoring API Service to original from {latest_api[1]} (Size: {latest_api[2]} bytes, Modified: {latest_api[3]})...")
    dest_api = "/home/abuhaneefayn/Desktop/4school/4school/app/src/main/java/com/haneef/school/data/api/SchoolApiService.kt"
    os.makedirs(os.path.dirname(dest_api), exist_ok=True)
    with open(latest_api[1], "r", encoding="utf-8") as src, open(dest_api, "w", encoding="utf-8") as dest:
        dest.write(src.read())
    print("API Service restored successfully.")

  - Handles confirmation dialogs for deletion.
  - Resolved compiler issues regarding missing scroll state imports.

### 6. Navigation Flow (`SchoolApp.kt`)
- Registered the `"schedule/calendar"` route mapping to the newly created screen, linking it into the global application compose setup.

### 7. Verification
- Running `./gradlew :app:compileDebugKotlin` compiles successfully.

// MISSING LINE 76
// MISSING LINE 77
// MISSING LINE 78
// MISSING LINE 79
// MISSING LINE 80
// MISSING LINE 81
// MISSING LINE 82
// MISSING LINE 83
// MISSING LINE 84
// MISSING LINE 85
// MISSING LINE 86
// MISSING LINE 87
// MISSING LINE 88
// MISSING LINE 89
// MISSING LINE 90
// MISSING LINE 91
// MISSING LINE 92
// MISSING LINE 93
// MISSING LINE 94
// MISSING LINE 95
// MISSING LINE 96
// MISSING LINE 97
// MISSING LINE 98
// MISSING LINE 99
// MISSING LINE 100
// MISSING LINE 101
// MISSING LINE 102
// MISSING LINE 103
// MISSING LINE 104
// MISSING LINE 105
// MISSING LINE 106
// MISSING LINE 107
// MISSING LINE 108
// MISSING LINE 109
// MISSING LINE 110
// MISSING LINE 111
// MISSING LINE 112
// MISSING LINE 113
// MISSING LINE 114
// MISSING LINE 115

# Let's write a file containing the reconstruction from the latest conversation before current, or the current conversation.
# Let's merge the lines from the second to last conversation:
if len(convs) >= 2:
    prev_conv = convs[-2]
    print(f"\nMerging lines from previous conversation: {prev_conv}")
    prev_views = [v for v in views if prev_conv in v["path"]]
    merged_lines = {}
    for v in prev_views:
        merged_lines.update(v["lines"])

    max_l = max(merged_lines.keys()) if merged_lines else 0
    print(f"Merged {len(merged_lines)} lines out of {max_l} total lines.")

    # Write the reconstruction to a temp file
    recon_path = "/home/abuhaneefayn/Desktop/4school/reconstructed_prev.kt"
    with open(recon_path, "w", encoding="utf-8") as f:
        for i in range(1, max_l + 1):
            f.write(merged_lines.get(i, f"// MISSING LINE {i}\n") + "\n")
    print(f"Reconstruction written to {recon_path}")

# Let's also merge lines from the current conversation
print(f"\nMerging lines from current conversation: {current_conv}")
merged_current = {}
for v in current_conv_views:
    merged_current.update(v["lines"])

max_curr_l = max(merged_current.keys()) if merged_current else 0
print(f"Merged {len(merged_current)} lines out of {max_curr_l} total lines.")
recon_curr_path = "/home/abuhaneefayn/Desktop/4school/reconstructed_curr.kt"
with open(recon_curr_path, "w", encoding="utf-8") as f:
    for i in range(1, max_curr_l + 1):
        recon_line = merged_current.get(i)
        if recon_line is None:
            f.write(f"// MISSING LINE {i}\n")
        else:
            f.write(recon_line + "\n")
print(f"Reconstruction of current written to {recon_curr_path}")

// MISSING LINE 155
// MISSING LINE 156
// MISSING LINE 157
// MISSING LINE 158
// MISSING LINE 159
// MISSING LINE 160
// MISSING LINE 161
// MISSING LINE 162
// MISSING LINE 163
// MISSING LINE 164
// MISSING LINE 165
// MISSING LINE 166
// MISSING LINE 167
// MISSING LINE 168
// MISSING LINE 169
// MISSING LINE 170
// MISSING LINE 171
// MISSING LINE 172
// MISSING LINE 173
// MISSING LINE 174
// MISSING LINE 175
// MISSING LINE 176
// MISSING LINE 177
// MISSING LINE 178
// MISSING LINE 179
// MISSING LINE 180
// MISSING LINE 181
// MISSING LINE 182
// MISSING LINE 183
// MISSING LINE 184
// MISSING LINE 185
// MISSING LINE 186
// MISSING LINE 187
// MISSING LINE 188
// MISSING LINE 189
// MISSING LINE 190
// MISSING LINE 191
// MISSING LINE 192
// MISSING LINE 193
// MISSING LINE 194
// MISSING LINE 195
// MISSING LINE 196
// MISSING LINE 197
// MISSING LINE 198
// MISSING LINE 199
// MISSING LINE 200
// MISSING LINE 201
// MISSING LINE 202
// MISSING LINE 203
// MISSING LINE 204
// MISSING LINE 205
// MISSING LINE 206
// MISSING LINE 207
// MISSING LINE 208
// MISSING LINE 209
// MISSING LINE 210
// MISSING LINE 211
// MISSING LINE 212
// MISSING LINE 213
// MISSING LINE 214
// MISSING LINE 215
// MISSING LINE 216
// MISSING LINE 217
// MISSING LINE 218
// MISSING LINE 219
// MISSING LINE 220
// MISSING LINE 221
// MISSING LINE 222
// MISSING LINE 223
// MISSING LINE 224
// MISSING LINE 225
            ),
            AdminActivity(
                id = "admin_activity_3",
                description = "Generated monthly performance report",
                performedBy = "Admin User",
                date = java.util.Calendar.getInstance().apply {
                    add(java.util.Calendar.DAY_OF_YEAR, -2)
                }.time,
                type = AdminActivityType.REPORT_GENERATION,
                impact = "Report sent to 50+ stakeholders"
            )
        )
    }

    private fun generateDummySystemAlerts(): List<SystemAlert> {
        return listOf(
            SystemAlert(
                id = "alert_1",
                title = "Database Backup Completed",
                message = "Daily database backup completed successfully",
                type = SystemAlertType.SYSTEM_ERROR,
                severity = AlertSeverity.LOW,
                date = java.util.Date(),
                isResolved = true
            ),
            SystemAlert(
                id = "alert_2",
                title = "High Server Load",
                message = "Server experiencing high load during peak hours",
                type = SystemAlertType.PERFORMANCE_ISSUE,
                severity = AlertSeverity.MEDIUM,
                date = java.util.Date(),
                isResolved = false
            ),
            SystemAlert(
                id = "alert_3",
                title = "Storage Capacity Warning",
                message = "Storage capacity at 85%, consider upgrading",
                type = SystemAlertType.CAPACITY_WARNING,
                severity = AlertSeverity.HIGH,
                date = java.util.Date(),
                isResolved = false
            )
        )
    }
}
// MISSING LINE 272
// MISSING LINE 273
// MISSING LINE 274
// MISSING LINE 275
// MISSING LINE 276
        } catch (e: HttpException) {
            Result.failure(Exception("Network error: ${e.message()}"))
        } catch (e: IOException) {
            Result.failure(Exception("Network error: Please check your internet connection"))
        } catch (e: Exception) {
            Result.failure(Exception("Unexpected error: ${e.message}"))
        }
    }
    ): Response<List<Term>>

    @POST("auth/schedule/terms")
    suspend fun createTerm(
        @Header("Authorization") authorization: String,
        @Body request: CreateTermRequest
    ): Response<Term>

    @PUT("auth/schedule/terms/{id}")
    suspend fun updateTerm(
        @Path("id") termId: String,
        @Header("Authorization") authorization: String,
        @Body request: CreateTermRequest
    ): Response<Term>

    @DELETE("auth/schedule/terms/{id}")
    suspend fun deleteTerm(
        @Path("id") termId: String,
        @Header("Authorization") authorization: String,
        @Query("school_id") schoolId: String
    ): Response<okhttp3.ResponseBody>

    @GET("auth/schedule/school-timetable-items")
    suspend fun getSchoolTimetableItems(
        @Header("Authorization") authorization: String,
        @Query("school_id") schoolId: String
    ): Response<List<SchoolTimetable>>

    @POST("auth/schedule/school-timetable-items")
    suspend fun createSchoolTimetableItem(
        @Header("Authorization") authorization: String,
        @Body request: CreateSchoolTimetableRequest
    ): Response<List<SchoolTimetable>>

    @PUT("auth/schedule/school-timetable-items/{id}")
    suspend fun updateSchoolTimetableItem(
        @Path("id") itemId: String,
        @Header("Authorization") authorization: String,
        @Body request: UpdateSchoolTimetableRequest
    ): Response<SchoolTimetable>

    @DELETE("auth/schedule/school-timetable-items/{id}")
    suspend fun deleteSchoolTimetableItem(
        @Path("id") itemId: String,
        @Header("Authorization") authorization: String,
        @Query("school_id") schoolId: String
    ): Response<okhttp3.ResponseBody>
}

// MISSING LINE 334
// MISSING LINE 335
// MISSING LINE 336
// MISSING LINE 337
// MISSING LINE 338
// MISSING LINE 339
// MISSING LINE 340
// MISSING LINE 341
// MISSING LINE 342
// MISSING LINE 343
// MISSING LINE 344
// MISSING LINE 345
// MISSING LINE 346
// MISSING LINE 347
// MISSING LINE 348
// MISSING LINE 349
// MISSING LINE 350
// MISSING LINE 351
// MISSING LINE 352
// MISSING LINE 353
// MISSING LINE 354
// MISSING LINE 355
// MISSING LINE 356
// MISSING LINE 357
// MISSING LINE 358
// MISSING LINE 359
// MISSING LINE 360
            val response = apiService.getCurrentSchoolData(
                schoolId = schoolId,
                authorization = "Bearer $accessToken"
            )
            if (response.isSuccessful) {
                response.body()?.let { schoolData ->
                    Result.success(schoolData)
                } ?: Result.failure(Exception("Empty response body"))
            } else {
                val errorBody = response.errorBody()?.string()
                val apiError = errorBody?.let {
                    try {
                        gson.fromJson(it, ApiError::class.java)
                    } catch (e: Exception) {
                        null
                    }
                }
                val errorMessage = apiError?.message ?: "Failed to get current school data: ${response.message()}"
                Result.failure(Exception(errorMessage))
            }
            }
        catch (e: HttpException) {
            Result.failure(Exception("Network error: ${e.message()}"))
        } catch (e: IOException) {
            Result.failure(Exception("Network error: Please check your internet connection"))
        } catch (e: Exception) {
            Result.failure(Exception("Unexpected error: ${e.message}"))
        }
    }

    suspend fun getStaffList(
        accessToken: String,
        schoolId: String,
        page: Int
// MISSING LINE 395
// MISSING LINE 396
// MISSING LINE 397
// MISSING LINE 398
// MISSING LINE 399
// MISSING LINE 400
// MISSING LINE 401
// MISSING LINE 402
// MISSING LINE 403
// MISSING LINE 404
// MISSING LINE 405
// MISSING LINE 406
// MISSING LINE 407
// MISSING LINE 408
// MISSING LINE 409
// MISSING LINE 410
// MISSING LINE 411
// MISSING LINE 412
// MISSING LINE 413
// MISSING LINE 414
// MISSING LINE 415
// MISSING LINE 416
// MISSING LINE 417
// MISSING LINE 418
// MISSING LINE 419
// MISSING LINE 420
// MISSING LINE 421
// MISSING LINE 422
// MISSING LINE 423
// MISSING LINE 424
// MISSING LINE 425
// MISSING LINE 426
// MISSING LINE 427
// MISSING LINE 428
// MISSING LINE 429
// MISSING LINE 430
// MISSING LINE 431
// MISSING LINE 432
// MISSING LINE 433
// MISSING LINE 434
// MISSING LINE 435
// MISSING LINE 436
// MISSING LINE 437
// MISSING LINE 438
// MISSING LINE 439
// MISSING LINE 440
// MISSING LINE 441
// MISSING LINE 442
// MISSING LINE 443
// MISSING LINE 444
// MISSING LINE 445
// MISSING LINE 446
// MISSING LINE 447
// MISSING LINE 448
// MISSING LINE 449
// MISSING LINE 450
// MISSING LINE 451
// MISSING LINE 452
// MISSING LINE 453
// MISSING LINE 454
// MISSING LINE 455
// MISSING LINE 456
// MISSING LINE 457
// MISSING LINE 458
// MISSING LINE 459
// MISSING LINE 460
// MISSING LINE 461
// MISSING LINE 462
// MISSING LINE 463
// MISSING LINE 464
// MISSING LINE 465
// MISSING LINE 466
// MISSING LINE 467
// MISSING LINE 468
// MISSING LINE 469
// MISSING LINE 470
// MISSING LINE 471
// MISSING LINE 472
// MISSING LINE 473
// MISSING LINE 474
// MISSING LINE 475
// MISSING LINE 476
// MISSING LINE 477
// MISSING LINE 478
// MISSING LINE 479
// MISSING LINE 480
// MISSING LINE 481
// MISSING LINE 482
// MISSING LINE 483
// MISSING LINE 484
// MISSING LINE 485
// MISSING LINE 486
// MISSING LINE 487
// MISSING LINE 488
// MISSING LINE 489
// MISSING LINE 490
// MISSING LINE 491
// MISSING LINE 492
// MISSING LINE 493
// MISSING LINE 494
// MISSING LINE 495
// MISSING LINE 496
// MISSING LINE 497
// MISSING LINE 498
// MISSING LINE 499
// MISSING LINE 500
// MISSING LINE 501
// MISSING LINE 502
// MISSING LINE 503
// MISSING LINE 504
// MISSING LINE 505
// MISSING LINE 506
// MISSING LINE 507
// MISSING LINE 508
// MISSING LINE 509
// MISSING LINE 510
// MISSING LINE 511
// MISSING LINE 512
// MISSING LINE 513
// MISSING LINE 514
// MISSING LINE 515
// MISSING LINE 516
// MISSING LINE 517
// MISSING LINE 518
// MISSING LINE 519
// MISSING LINE 520
// MISSING LINE 521
// MISSING LINE 522
// MISSING LINE 523
// MISSING LINE 524
// MISSING LINE 525
// MISSING LINE 526
// MISSING LINE 527
// MISSING LINE 528
// MISSING LINE 529
// MISSING LINE 530
// MISSING LINE 531
// MISSING LINE 532
// MISSING LINE 533
// MISSING LINE 534
// MISSING LINE 535
// MISSING LINE 536
// MISSING LINE 537
// MISSING LINE 538
// MISSING LINE 539
// MISSING LINE 540
// MISSING LINE 541
// MISSING LINE 542
// MISSING LINE 543
// MISSING LINE 544
// MISSING LINE 545
// MISSING LINE 546
// MISSING LINE 547
// MISSING LINE 548
// MISSING LINE 549
// MISSING LINE 550
// MISSING LINE 551
// MISSING LINE 552
// MISSING LINE 553
// MISSING LINE 554
// MISSING LINE 555
// MISSING LINE 556
// MISSING LINE 557
// MISSING LINE 558
// MISSING LINE 559
// MISSING LINE 560
// MISSING LINE 561
// MISSING LINE 562
// MISSING LINE 563
// MISSING LINE 564
// MISSING LINE 565
// MISSING LINE 566
// MISSING LINE 567
// MISSING LINE 568
// MISSING LINE 569
// MISSING LINE 570
// MISSING LINE 571
// MISSING LINE 572
// MISSING LINE 573
// MISSING LINE 574
// MISSING LINE 575
// MISSING LINE 576
// MISSING LINE 577
// MISSING LINE 578
// MISSING LINE 579
// MISSING LINE 580
// MISSING LINE 581
// MISSING LINE 582
// MISSING LINE 583
// MISSING LINE 584
// MISSING LINE 585
// MISSING LINE 586
// MISSING LINE 587
// MISSING LINE 588
// MISSING LINE 589
// MISSING LINE 590
// MISSING LINE 591
// MISSING LINE 592
// MISSING LINE 593
// MISSING LINE 594
// MISSING LINE 595
// MISSING LINE 596
// MISSING LINE 597
// MISSING LINE 598
// MISSING LINE 599
// MISSING LINE 600
// MISSING LINE 601
// MISSING LINE 602
// MISSING LINE 603
// MISSING LINE 604
// MISSING LINE 605
// MISSING LINE 606
// MISSING LINE 607
// MISSING LINE 608
// MISSING LINE 609
// MISSING LINE 610
// MISSING LINE 611
// MISSING LINE 612
// MISSING LINE 613
// MISSING LINE 614
// MISSING LINE 615
// MISSING LINE 616
// MISSING LINE 617
// MISSING LINE 618
// MISSING LINE 619
// MISSING LINE 620
// MISSING LINE 621
// MISSING LINE 622
// MISSING LINE 623
// MISSING LINE 624
// MISSING LINE 625
// MISSING LINE 626
// MISSING LINE 627
// MISSING LINE 628
// MISSING LINE 629
// MISSING LINE 630
// MISSING LINE 631
// MISSING LINE 632
// MISSING LINE 633
// MISSING LINE 634
// MISSING LINE 635
// MISSING LINE 636
// MISSING LINE 637
// MISSING LINE 638
// MISSING LINE 639
// MISSING LINE 640
// MISSING LINE 641
// MISSING LINE 642
// MISSING LINE 643
// MISSING LINE 644
// MISSING LINE 645
// MISSING LINE 646
// MISSING LINE 647
// MISSING LINE 648
// MISSING LINE 649
// MISSING LINE 650
// MISSING LINE 651
// MISSING LINE 652
// MISSING LINE 653
// MISSING LINE 654
// MISSING LINE 655
// MISSING LINE 656
// MISSING LINE 657
// MISSING LINE 658
// MISSING LINE 659
// MISSING LINE 660
// MISSING LINE 661
// MISSING LINE 662
// MISSING LINE 663
// MISSING LINE 664
// MISSING LINE 665
// MISSING LINE 666
// MISSING LINE 667
// MISSING LINE 668
// MISSING LINE 669
// MISSING LINE 670
// MISSING LINE 671
// MISSING LINE 672
// MISSING LINE 673
// MISSING LINE 674
// MISSING LINE 675
// MISSING LINE 676
// MISSING LINE 677
// MISSING LINE 678
// MISSING LINE 679
// MISSING LINE 680
// MISSING LINE 681
// MISSING LINE 682
// MISSING LINE 683
// MISSING LINE 684
// MISSING LINE 685
// MISSING LINE 686
// MISSING LINE 687
// MISSING LINE 688
// MISSING LINE 689
// MISSING LINE 690
// MISSING LINE 691
// MISSING LINE 692
// MISSING LINE 693
// MISSING LINE 694
// MISSING LINE 695
// MISSING LINE 696
// MISSING LINE 697
// MISSING LINE 698
// MISSING LINE 699
// MISSING LINE 700
// MISSING LINE 701
// MISSING LINE 702
// MISSING LINE 703
// MISSING LINE 704
// MISSING LINE 705
// MISSING LINE 706
// MISSING LINE 707
// MISSING LINE 708
// MISSING LINE 709
// MISSING LINE 710
// MISSING LINE 711
// MISSING LINE 712
// MISSING LINE 713
// MISSING LINE 714
// MISSING LINE 715
// MISSING LINE 716
// MISSING LINE 717
// MISSING LINE 718
// MISSING LINE 719
// MISSING LINE 720
// MISSING LINE 721
// MISSING LINE 722
// MISSING LINE 723
// MISSING LINE 724
// MISSING LINE 725
// MISSING LINE 726
// MISSING LINE 727
// MISSING LINE 728
// MISSING LINE 729
// MISSING LINE 730
// MISSING LINE 731
// MISSING LINE 732
// MISSING LINE 733
// MISSING LINE 734
// MISSING LINE 735
// MISSING LINE 736
// MISSING LINE 737
// MISSING LINE 738
// MISSING LINE 739
// MISSING LINE 740
// MISSING LINE 741
// MISSING LINE 742
// MISSING LINE 743
// MISSING LINE 744
// MISSING LINE 745
// MISSING LINE 746
// MISSING LINE 747
// MISSING LINE 748
// MISSING LINE 749
// MISSING LINE 750
// MISSING LINE 751
// MISSING LINE 752
// MISSING LINE 753
// MISSING LINE 754
// MISSING LINE 755
// MISSING LINE 756
// MISSING LINE 757
// MISSING LINE 758
// MISSING LINE 759
// MISSING LINE 760
// MISSING LINE 761
// MISSING LINE 762
// MISSING LINE 763
// MISSING LINE 764
// MISSING LINE 765
// MISSING LINE 766
// MISSING LINE 767
// MISSING LINE 768
// MISSING LINE 769
// MISSING LINE 770
// MISSING LINE 771
// MISSING LINE 772
// MISSING LINE 773
// MISSING LINE 774
// MISSING LINE 775
// MISSING LINE 776
// MISSING LINE 777
// MISSING LINE 778
// MISSING LINE 779
// MISSING LINE 780
// MISSING LINE 781
// MISSING LINE 782
// MISSING LINE 783
// MISSING LINE 784
// MISSING LINE 785
// MISSING LINE 786
// MISSING LINE 787
// MISSING LINE 788
// MISSING LINE 789
// MISSING LINE 790
// MISSING LINE 791
// MISSING LINE 792
// MISSING LINE 793
// MISSING LINE 794
// MISSING LINE 795
// MISSING LINE 796
// MISSING LINE 797
// MISSING LINE 798
// MISSING LINE 799
// MISSING LINE 800
// MISSING LINE 801
// MISSING LINE 802
// MISSING LINE 803
// MISSING LINE 804
// MISSING LINE 805
// MISSING LINE 806
// MISSING LINE 807
// MISSING LINE 808
// MISSING LINE 809
// MISSING LINE 810
// MISSING LINE 811
// MISSING LINE 812
// MISSING LINE 813
// MISSING LINE 814
// MISSING LINE 815
// MISSING LINE 816
// MISSING LINE 817
// MISSING LINE 818
// MISSING LINE 819
// MISSING LINE 820
// MISSING LINE 821
// MISSING LINE 822
// MISSING LINE 823
// MISSING LINE 824
// MISSING LINE 825
// MISSING LINE 826
// MISSING LINE 827
// MISSING LINE 828
// MISSING LINE 829
// MISSING LINE 830
// MISSING LINE 831
// MISSING LINE 832
// MISSING LINE 833
// MISSING LINE 834
// MISSING LINE 835
// MISSING LINE 836
// MISSING LINE 837
// MISSING LINE 838
// MISSING LINE 839
// MISSING LINE 840
// MISSING LINE 841
// MISSING LINE 842
// MISSING LINE 843
// MISSING LINE 844
// MISSING LINE 845
// MISSING LINE 846
// MISSING LINE 847
// MISSING LINE 848
// MISSING LINE 849
// MISSING LINE 850
// MISSING LINE 851
// MISSING LINE 852
// MISSING LINE 853
// MISSING LINE 854
// MISSING LINE 855
// MISSING LINE 856
// MISSING LINE 857
// MISSING LINE 858
// MISSING LINE 859
// MISSING LINE 860
// MISSING LINE 861
// MISSING LINE 862
// MISSING LINE 863
// MISSING LINE 864
// MISSING LINE 865
// MISSING LINE 866
// MISSING LINE 867
// MISSING LINE 868
// MISSING LINE 869
// MISSING LINE 870
// MISSING LINE 871
// MISSING LINE 872
// MISSING LINE 873
// MISSING LINE 874
// MISSING LINE 875
// MISSING LINE 876
// MISSING LINE 877
// MISSING LINE 878
// MISSING LINE 879
// MISSING LINE 880
// MISSING LINE 881
// MISSING LINE 882
// MISSING LINE 883
// MISSING LINE 884
// MISSING LINE 885
// MISSING LINE 886
// MISSING LINE 887
// MISSING LINE 888
// MISSING LINE 889
// MISSING LINE 890
// MISSING LINE 891
// MISSING LINE 892
// MISSING LINE 893
// MISSING LINE 894
// MISSING LINE 895
// MISSING LINE 896
// MISSING LINE 897
// MISSING LINE 898
// MISSING LINE 899
// MISSING LINE 900
// MISSING LINE 901
// MISSING LINE 902
// MISSING LINE 903
// MISSING LINE 904
// MISSING LINE 905
// MISSING LINE 906
// MISSING LINE 907
// MISSING LINE 908
// MISSING LINE 909
// MISSING LINE 910
// MISSING LINE 911
// MISSING LINE 912
// MISSING LINE 913
// MISSING LINE 914
// MISSING LINE 915
// MISSING LINE 916
// MISSING LINE 917
// MISSING LINE 918
// MISSING LINE 919
// MISSING LINE 920
// MISSING LINE 921
// MISSING LINE 922
// MISSING LINE 923
// MISSING LINE 924
// MISSING LINE 925
// MISSING LINE 926
// MISSING LINE 927
// MISSING LINE 928
// MISSING LINE 929
// MISSING LINE 930
// MISSING LINE 931
// MISSING LINE 932
// MISSING LINE 933
// MISSING LINE 934
// MISSING LINE 935
// MISSING LINE 936
// MISSING LINE 937
// MISSING LINE 938
// MISSING LINE 939
// MISSING LINE 940
// MISSING LINE 941
// MISSING LINE 942
// MISSING LINE 943
// MISSING LINE 944
// MISSING LINE 945
// MISSING LINE 946
// MISSING LINE 947
// MISSING LINE 948
// MISSING LINE 949
// MISSING LINE 950
// MISSING LINE 951
// MISSING LINE 952
// MISSING LINE 953
// MISSING LINE 954
// MISSING LINE 955
// MISSING LINE 956
// MISSING LINE 957
// MISSING LINE 958
// MISSING LINE 959
// MISSING LINE 960
// MISSING LINE 961
// MISSING LINE 962
// MISSING LINE 963
// MISSING LINE 964
// MISSING LINE 965
// MISSING LINE 966
// MISSING LINE 967
// MISSING LINE 968
// MISSING LINE 969
// MISSING LINE 970
// MISSING LINE 971
// MISSING LINE 972
// MISSING LINE 973
// MISSING LINE 974
// MISSING LINE 975
// MISSING LINE 976
// MISSING LINE 977
// MISSING LINE 978
// MISSING LINE 979
// MISSING LINE 980
// MISSING LINE 981
// MISSING LINE 982
// MISSING LINE 983
// MISSING LINE 984
// MISSING LINE 985
// MISSING LINE 986
// MISSING LINE 987
// MISSING LINE 988
// MISSING LINE 989
// MISSING LINE 990
// MISSING LINE 991
// MISSING LINE 992
// MISSING LINE 993
// MISSING LINE 994
// MISSING LINE 995
// MISSING LINE 996
// MISSING LINE 997
// MISSING LINE 998
// MISSING LINE 999
// MISSING LINE 1000
// MISSING LINE 1001
// MISSING LINE 1002
// MISSING LINE 1003
// MISSING LINE 1004
// MISSING LINE 1005
// MISSING LINE 1006
// MISSING LINE 1007
// MISSING LINE 1008
// MISSING LINE 1009
// MISSING LINE 1010
// MISSING LINE 1011
// MISSING LINE 1012
// MISSING LINE 1013
// MISSING LINE 1014
// MISSING LINE 1015
// MISSING LINE 1016
// MISSING LINE 1017
// MISSING LINE 1018
// MISSING LINE 1019
// MISSING LINE 1020
// MISSING LINE 1021
// MISSING LINE 1022
// MISSING LINE 1023
// MISSING LINE 1024
// MISSING LINE 1025
// MISSING LINE 1026
// MISSING LINE 1027
// MISSING LINE 1028
// MISSING LINE 1029
// MISSING LINE 1030
// MISSING LINE 1031
// MISSING LINE 1032
// MISSING LINE 1033
// MISSING LINE 1034
// MISSING LINE 1035
// MISSING LINE 1036
// MISSING LINE 1037
// MISSING LINE 1038
// MISSING LINE 1039
// MISSING LINE 1040
// MISSING LINE 1041
// MISSING LINE 1042
// MISSING LINE 1043
// MISSING LINE 1044
// MISSING LINE 1045
// MISSING LINE 1046
// MISSING LINE 1047
// MISSING LINE 1048
// MISSING LINE 1049
// MISSING LINE 1050
// MISSING LINE 1051
// MISSING LINE 1052
// MISSING LINE 1053
// MISSING LINE 1054
// MISSING LINE 1055
// MISSING LINE 1056
// MISSING LINE 1057
// MISSING LINE 1058
// MISSING LINE 1059
// MISSING LINE 1060
// MISSING LINE 1061
// MISSING LINE 1062
// MISSING LINE 1063
// MISSING LINE 1064
// MISSING LINE 1065
// MISSING LINE 1066
// MISSING LINE 1067
// MISSING LINE 1068
// MISSING LINE 1069
// MISSING LINE 1070
// MISSING LINE 1071
// MISSING LINE 1072
// MISSING LINE 1073
// MISSING LINE 1074
// MISSING LINE 1075
// MISSING LINE 1076
// MISSING LINE 1077
// MISSING LINE 1078
// MISSING LINE 1079
// MISSING LINE 1080
// MISSING LINE 1081
// MISSING LINE 1082
// MISSING LINE 1083
// MISSING LINE 1084
// MISSING LINE 1085
// MISSING LINE 1086
// MISSING LINE 1087
// MISSING LINE 1088
// MISSING LINE 1089
// MISSING LINE 1090
// MISSING LINE 1091
// MISSING LINE 1092
// MISSING LINE 1093
// MISSING LINE 1094
// MISSING LINE 1095
// MISSING LINE 1096
// MISSING LINE 1097
    suspend fun createSchoolTimetableItem(
        accessToken: String,
        request: CreateSchoolTimetableRequest
    ): Result<List<SchoolTimetable>> {
        return try {
            val response = apiService.createSchoolTimetableItem("Bearer $accessToken", request)
            if (response.isSuccessful) {
                Result.success(response.body() ?: emptyList())
            } else {
                val errorBody = response.errorBody()?.string()
                val apiError = errorBody?.let {
                    try { gson.fromJson(it, ApiError::class.java) } catch (e: Exception) { null }
                }
                Result.failure(Exception(apiError?.message ?: "Failed to create timetable item: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateSchoolTimetableItem(
        itemId: String,
        accessToken: String,
        request: UpdateSchoolTimetableRequest
    ): Result<SchoolTimetable> {
        return try {
            val response = apiService.updateSchoolTimetableItem(itemId, "Bearer $accessToken", request)
            if (response.isSuccessful) {
                val body = response.body() ?: return Result.failure(Exception("Response body was null"))
                Result.success(body)
            } else {
                val errorBody = response.errorBody()?.string()
                val apiError = errorBody?.let {
                    try { gson.fromJson(it, ApiError::class.java) } catch (e: Exception) { null }
                }
                Result.failure(Exception(apiError?.message ?: "Failed to update timetable item: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

        }
    }

    suspend fun deleteSchoolTimetableItem(
        itemId: String,
        accessToken: String,
        schoolId: String
    ): Result<String> {
        return try {
            val response = apiService.deleteSchoolTimetableItem(itemId, "Bearer $accessToken", schoolId)
            if (response.isSuccessful) {
                Result.success("Timetable item deleted successfully")
            } else {
                val errorBody = response.errorBody()?.string()
                val apiError = errorBody?.let {
                    try { gson.fromJson(it, ApiError::class.java) } catch (e: Exception) { null }
                }
                Result.failure(Exception(apiError?.message ?: "Failed to delete timetable item: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
