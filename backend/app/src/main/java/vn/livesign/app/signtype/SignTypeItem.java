package vn.livesign.app.signtype;

import java.util.List;

/** Một phần tử của GET /sign-types, tên trường theo docs/contracts/api.md. */
public record SignTypeItem(int classId, String signTypeCode, String nameVi, List<String> vehicleTypes) {}
