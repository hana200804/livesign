package vn.livesign.core.catalog;

import java.util.List;

/**
 * Một loại biển trong danh mục classes.yaml.
 *
 * @param classId chỉ số lớp của model, cũng là thứ tự trong danh mục
 * @param code mã QCVN, ví dụ {@code P.131a}
 * @param vehicleTypes loại xe biển áp dụng, con của {@link SignCatalog#vehicleTypes()}
 * @param mapillaryLabelCandidates nhãn feature Mapillary có thể ánh xạ sang mã này
 */
public record SignType(
    int classId,
    String code,
    String nameVi,
    String group,
    List<String> vehicleTypes,
    List<String> mapillaryLabelCandidates) {

  public SignType {
    vehicleTypes = List.copyOf(vehicleTypes);
    mapillaryLabelCandidates = List.copyOf(mapillaryLabelCandidates);
  }

  public boolean appliesTo(String vehicleType) {
    return vehicleTypes.contains(vehicleType);
  }
}
